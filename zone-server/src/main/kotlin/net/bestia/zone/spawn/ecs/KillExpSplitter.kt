package net.bestia.zone.spawn.ecs

import net.bestia.zone.config.PartyRulesConfig
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Who gets the EXP each attacker of a kill earned, once [ExperienceGainCalculator] has portioned it out.
 *
 * - Outside a party, or in one that has each member take their own: the attacker keeps its portion.
 * - In an Even share party: the portion gets [PartyRulesConfig.evenShareBonusPercent] on top and is split
 *   evenly between every member near the kill - the attacker's account always, the rest when their master or
 *   the entity they play stands within [PartyRulesConfig.expShareRangeMetres], flat on the map. Each share goes
 *   to the member's master. With nobody else near the kill the attacker keeps its portion, without a bonus:
 *   the bonus is for sharing, not for being in a party.
 *
 * Tick thread: reads the world and the session map, nothing else.
 */
@Component
class KillExpSplitter(
  private val sessions: ConnectionInfoService,
  private val rules: PartyRulesConfig,
) {

  /** [amount] EXP for [entityId]. [masterEntityId] is the master behind it, which a levy taxes, or null. */
  data class ExpCredit(val entityId: EntityId, val masterEntityId: EntityId?, val amount: Int)

  val reads: ComponentClassSet = setOf(
    Account::class,
    Master::class,
    OwnedBestia::class,
    PartyMembership::class,
    Position::class
  )

  /** @param killedAt where the kill happened, or null when it had no position: then nobody shares it */
  fun split(world: World, killedAt: Vec3L?, earned: Map<EntityId, Int>): List<ExpCredit> {
    val credits = ArrayList<ExpCredit>(earned.size)

    for ((attacker, exp) in earned) {
      if (exp <= 0) {
        continue
      }

      val masterEntityId = masterEntityOf(world, attacker)
      val sharers = if (killedAt == null || masterEntityId == null) null else sharersOf(world, attacker, masterEntityId, killedAt)

      if (sharers == null || sharers.size < 2) {
        credits.add(ExpCredit(attacker, masterEntityId, exp))
        continue
      }

      val pool = exp + exp * rules.evenShareBonusPercent / 100
      val share = pool / sharers.size
      if (share > 0) {
        sharers.forEach { credits.add(ExpCredit(it, it, share)) }
      }
    }

    return credits
  }

  /**
   * The master entity behind [entityId]: a master is its own, and a bestia's is its owner's while the owner
   * plays the master it belongs to. Null for anything else, which keeps its EXP untaxed and unshared.
   */
  fun masterEntityOf(world: World, entityId: EntityId): EntityId? {
    if (world.has(entityId, Master::class)) {
      return entityId
    }

    val accountId = world.get(entityId, Account::class)?.accountId ?: return null
    val ownedBestia = world.get(entityId, OwnedBestia::class) ?: return null
    if (!sessions.isPlaying(accountId, ownedBestia.masterId)) {
      return null
    }

    return selectedMasterEntityOf(accountId)
  }

  /** The master entities sharing [attacker]'s portion, or null when its party takes each their own. */
  private fun sharersOf(
    world: World,
    attacker: EntityId,
    attackerMaster: EntityId,
    killedAt: Vec3L,
  ): Set<EntityId>? {
    val membership = world.get(attackerMaster, PartyMembership::class) ?: return null
    if (membership.expShare != PartyExpShare.EVEN_SHARE) {
      return null
    }

    val attackerAccount = world.get(attacker, Account::class)?.accountId

    val sharers = LinkedHashSet<EntityId>()
    sharers.add(attackerMaster)

    for (member in membership.members) {
      if (member.accountId == attackerAccount || !sessions.isPlaying(member.accountId, member.masterId)) {
        continue
      }

      val memberMaster = selectedMasterEntityOf(member.accountId) ?: continue
      val played = activeEntityOf(member.accountId) ?: memberMaster

      if (isNear(world, memberMaster, killedAt) || (played != memberMaster && isNear(world, played, killedAt))) {
        sharers.add(memberMaster)
      }
    }

    return sharers
  }

  private fun isNear(world: World, entityId: EntityId, at: Vec3L): Boolean {
    val position = world.get(entityId, Position::class) ?: return false
    val dx = position.x - at.x
    val dy = position.y - at.y
    val range = rules.expShareRangeMetres.toLong()

    return dx * dx + dy * dy <= range * range
  }

  private fun selectedMasterEntityOf(accountId: AccountId): EntityId? {
    return try {
      sessions.getSelectedMasterEntityId(accountId)
    } catch (_: NoActiveSessionException) {
      null
    }
  }

  private fun activeEntityOf(accountId: AccountId): EntityId? {
    return try {
      sessions.getActiveEntityId(accountId)
    } catch (_: NoActiveSessionException) {
      null
    }
  }
}
