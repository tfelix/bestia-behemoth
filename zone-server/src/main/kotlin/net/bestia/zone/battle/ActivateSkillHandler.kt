package net.bestia.zone.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.NoSkillScriptException
import net.bestia.zone.battle.skill.SkillCheckService
import net.bestia.zone.battle.skill.SkillStrategyFactory
import net.bestia.zone.ecs.battle.skill.Casting
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.world.prop.PropPromotionService
import org.springframework.stereotype.Component

/**
 * Handles a player activating a learned skill from the UI (Skills window or hotbar), for whichever
 * entity (master or an owned bestia) is currently active.
 *
 * Validates that the skill is known at the requested level and that its cost can be met, then either
 * resolves it immediately or - when the skill has a cast time - attaches a [Casting] component and lets
 * [net.bestia.zone.ecs.battle.skill.CastingSystem] resolve it when the channel finishes.
 */
@Component
class ActivateSkillHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val skillCheckService: SkillCheckService,
  private val world: WorldView,
  private val skillStrategyFactory: SkillStrategyFactory,
  private val propPromotion: PropPromotionService,
  private val outMessageProcessor: OutMessageProcessor,
) : InMessageProcessor.IncomingMessageHandler<ActivateSkillCMSG> {
  override val handles = ActivateSkillCMSG::class

  override fun handle(msg: ActivateSkillCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    val knowsSkill = skillCheckService.knowsSkill(activeEntityId, msg.attackId, msg.skillLevel)

    if (!knowsSkill) {
      LOG.warn { "Entity $activeEntityId does not know attack ${msg.attackId} at level ${msg.skillLevel}, ignoring activation" }
      return true
    }

    val strategy = try {
      skillStrategyFactory.getSkillStrategy(msg.attackId)
    } catch (e: NoSkillScriptException) {
      // TODO improve the error message here as most info aleady is in e.message.
      LOG.warn { "Entity $activeEntityId activated ${skill.identifier}, which has no script: ${e.message}" }
      return true
    }

    LOG.info { "Skill activated: ${msg.attackId} Lv. ${msg.skillLevel} at ${msg.targetPosition}" }

    // why is there no world.read(entityId) equivalent? because this should not be a write but only a read.
    val isCastDenied = world.modify(activeEntityId) { id -> strategy.checkCastStart(this, id, msg.skillLevel) }
    if (isCastDenied != null) {
      LOG.debug { "Activation of ${skill.identifier} by $activeEntityId refused: $isCastDenied" }
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(isCastDenied))
      return true
    }

    // An entity-targeted skill carries a target id (the client sends 0 when nothing was picked); a
    // ground-targeted one falls back to the position, which is always present on the wire.
    val targetEntityId = msg.targetEntityId.takeIf { it != 0L }
    val targetPosition: Vec3L? = if (targetEntityId == null) msg.targetPosition else null

    world.modify(activeEntityId) { id ->
      // Before the cast-time branch, deliberately: a message-handler context never runs nested inside
      // scheduler.tick(), so this add() applies immediately - unlike promoting only from
      // BattleContextFactory, which a channelled cast reaches from inside CastingSystem.update() and would
      // silently fizzle its first hit against a pristine prop. See PropPromotionService's own KDoc.
      if (targetEntityId != null) {
        propPromotion.promoteIfNeeded(this, targetEntityId)
      }

      add(
        id, Casting(
          skillId = skill.id,
          skillLevel = msg.skillLevel,
          targetEntityId = targetEntityId,
          targetPosition = targetPosition,
          totalSeconds = skill.castTime
        )
      )
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
