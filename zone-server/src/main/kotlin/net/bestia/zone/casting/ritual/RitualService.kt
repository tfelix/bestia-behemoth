package net.bestia.zone.casting.ritual

import net.bestia.zone.battle.ecs.skill.RitualParticipant
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaConcentrationService
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

/**
 * Runs a ritual at a Mana Circle: who may lead and join, the cost of starting, and what the charge holds. The steps
 * and the numbers are the spell potency page's "How a Ritual Runs" and "The Charge".
 */
@Service
class RitualService(
  private val sessions: ConnectionInfoService,
  private val statusEffects: StatusEffectService,
  private val mana: ManaConcentrationService,
  private val structures: PlayerStructureRegistry,
  private val residency: WorldObjectResidencyService,
  private val worldService: WorldService,
  private val messages: OutMessageProcessor,
) {

  /**
   * A click on [circleId] by [actorId]. The owner's first click opens the ritual with their master as leader and
   * a second one starts it; anyone else's click joins as a helper.
   */
  fun interact(world: World, actorId: EntityId, circleId: EntityId): OpError? {
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null
    val ownerAccountId = world.get(circleId, PlayerStructureIdentity::class)?.ownerAccountId
    if (!isWithinReach(world, actorId, circleId)) return OpError.RITUAL_OUT_OF_RANGE
    if (isFatigued(world, actorId)) return OpError.RITUAL_FATIGUED

    val ritual = world.get(circleId, Ritual::class)
    return when {
      ritual == null && accountId == ownerAccountId -> open(world, accountId, circleId)
      ritual == null -> OpError.RITUAL_NOT_OPEN
      ritual.isRunning -> OpError.RITUAL_RUNNING
      accountId == ritual.leaderAccountId -> start(world, ritual)
      else -> join(world, ritual, accountId, actorId)
    }
  }

  private fun open(world: World, accountId: Long, circleId: EntityId): OpError? {
    val leaderId = sessions.getSelectedMasterEntityId(accountId)
    world.add(circleId, Ritual(leaderId, accountId))
    tell(accountId, OpSuccess.RITUAL_OPENED)

    return null
  }

  private fun join(world: World, ritual: Ritual, accountId: Long, helperId: EntityId): OpError? {
    if (ritual.helpers.containsKey(accountId)) return OpError.RITUAL_ALREADY_JOINED
    if (ritual.helpers.size >= MAX_HELPERS) return OpError.RITUAL_FULL

    ritual.helpers[accountId] = helperId
    tell(accountId, OpSuccess.RITUAL_JOINED)

    return null
  }

  /** Everyone pays a quarter of their maximum mana and starts to channel. A helper who cannot pay drops out. */
  private fun start(world: World, ritual: Ritual): OpError? {
    if (!payQuarterMana(world, ritual.leaderId)) return OpError.SKILL_NOT_ENOUGH_MANA

    ritual.helpers.entries.removeIf { (_, helperId) -> !payQuarterMana(world, helperId) }
    val circleId = circleOf(world, ritual) ?: return null
    for (participant in ritual.joined) {
      world.add(participant, RitualParticipant(circleId))
      statusEffects.applyEffect(world, participant, StatusEffectId.CHANNELING, 1, durationSeconds = CHANNEL_SECONDS)
    }
    ritual.remainingSeconds = CHANNEL_SECONDS.toFloat()
    tell(ritual.leaderAccountId, OpSuccess.RITUAL_STARTED)

    return null
  }

  /** The 30 seconds are over: the leader is charged, everyone rests and the ring is used up. */
  fun complete(world: World, circleId: EntityId, ritual: Ritual) {
    val at = world.get(circleId, Position::class)?.toVec3L()
    val factor = at?.let { mana.factorAt(it) } ?: 1.0
    val stayed = ritual.helpers.values.filter { world.has(it, RitualParticipant::class) }
    val bonus = (factor * stayed.sumOf { shareOf(world, it) }).toInt()
    val successBonus = (SUCCESS_PERCENT_PER_PARTICIPANT * (stayed.size + 1) * factor).roundToInt()

    statusEffects.applyEffect(world, ritual.leaderId, StatusEffectId.RITUAL_CHARGE, 1, amount = bonus)
    world.get(ritual.leaderId, StatusEffects::class)?.activeEffects
      ?.firstOrNull { it.definitionId == StatusEffectId.RITUAL_CHARGE.id }
      ?.count = successBonus
    tell(ritual.leaderAccountId, OpSuccess.RITUAL_CHARGED)

    end(world, circleId, ritual)
  }

  /** The leader dropped out: nothing is charged, but everyone still rests and the ring is still spent. */
  fun fail(world: World, circleId: EntityId, ritual: Ritual) {
    world.get(ritual.leaderId, Account::class)?.accountId?.let { messages.sendToPlayer(it, failure()) }
    end(world, circleId, ritual)
  }

  private fun end(world: World, circleId: EntityId, ritual: Ritual) {
    for (participant in ritual.joined) {
      if (world.has(participant, RitualParticipant::class)) {
        world.remove(participant, RitualParticipant::class)
        statusEffects.removeEffect(world, participant, StatusEffectId.CHANNELING.id, RemovalReason.CANCELLED)
      }
      statusEffects.applyEffect(world, participant, StatusEffectId.RITUAL_FATIGUE, 1)
    }
    removeCircle(world, circleId)
  }

  private fun removeCircle(world: World, circleId: EntityId) {
    val identity = world.get(circleId, PlayerStructureIdentity::class)
    val at = world.get(circleId, Position::class)?.toVec3L()
    residency.remove(world, circleId)
    if (identity == null || at == null) return

    val chunkSize = worldService.config.chunkSize.toLong()
    structures.remove(
      structureId = identity.structureId,
      chunkX = Math.floorDiv(at.x, chunkSize).toInt(),
      chunkY = Math.floorDiv(at.y, chunkSize).toInt()
    )
  }

  /** A helper's share: its level / 6, at most 20. A bestia helper counts with its own level. */
  private fun shareOf(world: World, helperId: EntityId): Int {
    val level = world.get(helperId, Level::class)?.level ?: 1

    return (level / LEVELS_PER_SHARE_POINT).coerceAtMost(MAX_SHARE)
  }

  private fun payQuarterMana(world: World, entityId: EntityId): Boolean {
    val pool = world.get(entityId, Mana::class) ?: return true
    val cost = pool.max / 4
    if (pool.current < cost) return false
    pool.current -= cost

    return true
  }

  private fun isFatigued(world: World, entityId: EntityId): Boolean {
    return world.get(entityId, StatusEffects::class)?.hasEffect(StatusEffectId.RITUAL_FATIGUE.id) == true
  }

  private fun isWithinReach(world: World, actorId: EntityId, circleId: EntityId): Boolean {
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return false
    val circleAt = world.get(circleId, Position::class)?.toVec3L() ?: return false

    return actorAt.distance(circleAt) <= REACH_TILES
  }

  private fun circleOf(world: World, ritual: Ritual): EntityId? {
    var found: EntityId? = null
    world.query(Ritual::class).each { id -> if (get<Ritual>() === ritual) found = id }

    return found
  }

  private fun tell(accountId: Long, code: OpSuccess) {
    messages.sendToPlayer(accountId, OperationSuccessSMSG(code))
  }

  private fun failure() = OperationErrorSMSG(OpError.RITUAL_FAILED)

  companion object {
    const val MAX_HELPERS = 5
    const val CHANNEL_SECONDS = 30.0
    const val REACH_TILES = 3L
    const val MAX_SHARE = 20
    const val LEVELS_PER_SHARE_POINT = 6
    const val SUCCESS_PERCENT_PER_PARTICIPANT = 5
  }
}
