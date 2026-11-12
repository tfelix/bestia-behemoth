package net.bestia.zone.casting.net

import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.battle.ActionGuard
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.skill.SkillTargetType
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.battle.net.HarmRefusal
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.logout.ecs.LogoutCancelService
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import net.bestia.zone.battle.CombatTargetPromotion
import net.bestia.zone.casting.NoSkillScriptException
import net.bestia.zone.casting.SkillCheckService
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.casting.SkillStrategyFactory
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.bnet.proto.OperationErrorProto.OpError

/**
 * Handles a player activating a learned skill from the UI (Skills window or hotbar), for whichever
 * entity (master or an owned bestia) is currently active.
 *
 * Validates that the skill is known at the requested level and that its cost can be met, then either
 * resolves it immediately or - when the skill has a cast time - attaches a [Casting] component and lets
 * [net.bestia.zone.casting.ecs.CastingSystem] resolve it when the channel finishes.
 *
 * A basic attack does **not** come through here: it has no catalogue row and no script, so it arrives as
 * an [net.bestia.zone.battle.net.AttackEntityCMSG] and is resolved by
 * [net.bestia.zone.battle.attack.AttackExecutionService] instead.
 */
@Component
class ActivateSkillHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val skillCheckService: SkillCheckService,
  private val skillStrategyFactory: SkillStrategyFactory,
  private val skillExecutionService: SkillExecutionService,
  private val logoutCancelService: LogoutCancelService,
  private val deadActionGuard: DeadActionGuard,
  private val actionGuard: ActionGuard,
  private val propPromotion: CombatTargetPromotion,
  private val outMessageProcessor: OutMessageProcessor,
  private val statusEffects: StatusEffectService,
) : TickMessageHandler<ActivateSkillCMSG> {
  override val wire = decoder(MessageCase.ACTIVATE_SKILL) { accountId, envelope ->
    ActivateSkillCMSG.fromBnet(accountId, envelope.activateSkill)
  }

  override fun handle(world: World, msg: ActivateSkillCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (deadActionGuard.refuses(world, activeEntityId, "activate a skill")) {
      return true
    }

    if (actionGuard.refuses(world, activeEntityId, ActionLock.CAST)) {
      return true
    }

    // A body halfway through a teleport's vanish is not there to cast. The client froze it, so this is a
    // hotkey pressed in the same half second, not a refusal worth telling.
    if (world.has(activeEntityId, Teleporting::class)) {
      return true
    }

    // Using a skill is player activity - abort any pending logout.
    logoutCancelService.cancelLogout(world, activeEntityId)

    val knowsSkill = skillCheckService.knowsSkill(activeEntityId, msg.attackId, msg.skillLevel)

    if (!knowsSkill) {
      LOG.warn { "Entity $activeEntityId does not know attack ${msg.attackId} at level ${msg.skillLevel}, ignoring activation" }
      return true
    }

    // The catalogue row is needed here and not only at resolution: the cast time decides between the two
    // branches below, and a channelled cast has to carry the skill id into its Casting component.
    val skill = skillExecutionService.skillOf(msg.attackId)
    if (skill == null) {
      LOG.warn { "Entity $activeEntityId activated unknown skill ${msg.attackId}, ignoring" }
      return true
    }

    val strategy = try {
      skillStrategyFactory.getSkillStrategy(skill)
    } catch (e: NoSkillScriptException) {
      // A passive, or an entry nobody has implemented. Refused here rather than at resolution so a
      // channelled one does not show a cast bar for a cast that was never going to happen.
      LOG.warn { "Activation by entity $activeEntityId refused: ${e.message}" }
      return true
    }

    // Before the cooldown check: ending a toggle is always allowed, and costs nothing.
    val toggled = strategy.toggledStatus
    if (toggled != null && statusEffects.cancelByBearer(world, activeEntityId, toggled.id)) {
      LOG.debug { "${skill.identifier} toggled off by $activeEntityId" }
      return true
    }

    // Also before the cooldown: a free choice like marking a portal spot is no cast and is never kept waiting.
    if (world.modify(activeEntityId) { id -> strategy.freeAction(this, id, msg.skillLevel, msg.args) } == true) {
      LOG.debug { "${skill.identifier} free action by $activeEntityId: ${msg.args}" }
      return true
    }

    // The client greys the button out for the same span, so a press that gets here is a hotkey race.
    if (!skillExecutionService.isReady(world, activeEntityId, skill.id)) {
      LOG.debug { "Activation of ${skill.identifier} by $activeEntityId refused: still cooling down" }
      return true
    }

    LOG.info { "Skill activated: ${skill.identifier} Lv. ${msg.skillLevel} at ${msg.targetPosition}" }

    // Before the cast starts, so a skill whose reagent is missing is refused while the
    // player is still looking at the button rather than after channelling for it. Nothing is spent here - see
    // SkillStrategy.checkCastStart.
    val cost = strategy.costOf(skill, msg.skillLevel)
    val denial = world.modify(activeEntityId) { id ->
      unaffordable(this, id, cost) ?: missingReagent(this, id, skill) ?: strategy.checkCastStart(this, id, msg.skillLevel, msg.args)
    }
    if (denial != null) {
      LOG.debug { "Activation of ${skill.identifier} by $activeEntityId refused: $denial" }
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(denial))
      return true
    }

    // The kind of target comes from the catalogue, not from what the client filled in. The client sends 0 when
    // nothing was picked, and the position is always present on the wire.
    val pickedEntityId = msg.targetEntityId.takeIf { it != 0L }
    val targetEntityId: EntityId?
    val targetPosition: Vec3L?
    when (skill.targetType) {
      SkillTargetType.GROUND, SkillTargetType.AOE_GROUND -> {
        targetEntityId = null
        targetPosition = msg.targetPosition
      }

      SkillTargetType.ENEMY -> {
        if (pickedEntityId == null) {
          LOG.debug { "Activation of ${skill.identifier} by $activeEntityId refused: no target picked" }
          return true
        }

        val verdict = DamageGate.verdict(world, activeEntityId, pickedEntityId)
        if (verdict != DamageGate.Verdict.ADMITTED) {
          HarmRefusal.send(outMessageProcessor, msg.playerId, verdict)
          return true
        }
        targetEntityId = pickedEntityId
        targetPosition = null
      }

      SkillTargetType.FRIENDLY -> {
        targetEntityId = pickedEntityId ?: activeEntityId
        targetPosition = null
      }
    }

    val targetDenial = world.modify(activeEntityId) { id ->
      strategy.checkTarget(this, id, targetEntityId, msg.skillLevel, msg.args)
    }
    if (targetDenial != null) {
      LOG.debug { "Activation of ${skill.identifier} by $activeEntityId refused for its target: $targetDenial" }
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(targetDenial))
      return true
    }

    val started = world.modify(activeEntityId) { id ->
      // Before the cast-time branch, deliberately: a tick handler runs between two ticks, so this add()
      // applies immediately - unlike promoting only from
      // BattleContextFactory, which a channelled cast reaches from inside CastingSystem.update() and would
      // silently fizzle its first hit against a pristine prop. See PropPromotionService's own KDoc.
      val caster = get(id, Position::class)?.toVec3L()
      if (targetEntityId != null && caster != null) {
        propPromotion.promoteIfNeeded(this, targetEntityId, caster, CombatTargetPromotion.TARGETING_REACH)
      }

      if (cost.castTimeSeconds > 0f) {
        // Starting a new cast supersedes whatever was being cast before.
        add(
          id, Casting(
            skillId = skill.id,
            skillLevel = msg.skillLevel,
            targetEntityId = targetEntityId,
            targetPosition = targetPosition,
            totalSeconds = reducedCastTime(cost.castTimeSeconds, get(id, CombatStats::class)?.values?.castTimeReduction ?: 0),
            args = msg.args
          )
        )
        if (Casting.movesWhileCasting(this, id)) add(id, IsStatusValueDirty)
        // Suffragium shortens only the next cast bar, so the bar that used it spends it.
        statusEffects.removeEffect(this, id, StatusEffectId.SUFFRAGIUM.id, RemovalReason.CANCELLED)
      }

      id
    } ?: return true

    if (cost.castTimeSeconds <= 0f) {
      skillExecutionService.execute(
        world = world,
        casterId = started,
        skillId = skill.id,
        skillLevel = msg.skillLevel,
        targetEntityId = targetEntityId,
        targetPosition = targetPosition,
        args = msg.args
      )
    }

    return true
  }

  /**
   * Refuses a cast its caster cannot pay for while the button is still under their finger. Only checked: the cost
   * is paid when the cast resolves, and checked again there.
   */
  private fun unaffordable(world: World, casterId: EntityId, cost: SkillLevelCost): OpError? {
    val mana = world.get(casterId, Mana::class)?.current ?: Int.MAX_VALUE
    val stamina = world.get(casterId, Stamina::class)?.current ?: Int.MAX_VALUE
    val staminaSaved = world.get(casterId, CombatStats::class)?.values?.staminaCostReduction ?: 0

    return when {
      mana < cost.manaCost -> OpError.SKILL_NOT_ENOUGH_MANA
      stamina < cost.staminaCostFor(staminaSaved) -> OpError.SKILL_NOT_ENOUGH_STAMINA
      else -> null
    }
  }

  /** A skill that uses up items is refused before its bar when the bag lacks one; they are taken on resolution. */
  private fun missingReagent(world: World, casterId: EntityId, skill: Skill): OpError? {
    val inventory = world.get(casterId, Inventory::class) ?: return null
    val lacksOne = skill.mechanics.reagents.any { reagent ->
      inventory.getItems().filter { it.itemId == reagent.itemId }.sumOf { it.amount } < reagent.amount
    }

    return if (lacksOne) OpError.SKILL_MISSING_REAGENT else null
  }

  private fun reducedCastTime(seconds: Float, reductionPercent: Int): Float {
    return seconds * (1f - reductionPercent / 100f).coerceAtLeast(MIN_CAST_TIME_SHARE)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** No amount of gear makes a cast instant: a fifth of it always remains. */
    private const val MIN_CAST_TIME_SHARE = 0.2f
  }
}
