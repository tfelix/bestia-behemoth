package net.bestia.zone.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.NoSkillScriptException
import net.bestia.zone.battle.skill.SkillCheckService
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.battle.skill.SkillStrategyFactory
import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.battle.skill.Casting
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.logout.LogoutCancelService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.world.prop.PropPromotionService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * Handles a player activating a learned skill from the UI (Skills window or hotbar), for whichever
 * entity (master or an owned bestia) is currently active.
 *
 * Validates that the skill is known at the requested level and that its cost can be met, then either
 * resolves it immediately or - when the skill has a cast time - attaches a [Casting] component and lets
 * [net.bestia.zone.ecs.battle.skill.CastingSystem] resolve it when the channel finishes.
 *
 * A basic attack does **not** come through here: it has no catalogue row and no script, so it arrives as
 * an [AttackEntityCMSG] and is resolved by
 * [net.bestia.zone.battle.skill.AttackExecutionService] instead.
 */
@Component
class ActivateSkillHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val skillCheckService: SkillCheckService,
  private val world: WorldView,
  private val skillRepository: SkillRepository,
  private val skillStrategyFactory: SkillStrategyFactory,
  private val skillExecutionService: SkillExecutionService,
  private val logoutCancelService: LogoutCancelService,
  private val deadActionGuard: DeadActionGuard,
  private val propPromotion: PropPromotionService,
  private val outMessageProcessor: OutMessageProcessor,
) : InMessageProcessor.IncomingMessageHandler<ActivateSkillCMSG> {
  override val handles = ActivateSkillCMSG::class

  override fun handle(msg: ActivateSkillCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (deadActionGuard.refuses(activeEntityId, "activate a skill")) {
      return true
    }

    // Using a skill is player activity - abort any pending logout.
    logoutCancelService.cancelLogout(activeEntityId)

    val knowsSkill = skillCheckService.knowsSkill(activeEntityId, msg.attackId, msg.skillLevel)

    if (!knowsSkill) {
      LOG.warn { "Entity $activeEntityId does not know attack ${msg.attackId} at level ${msg.skillLevel}, ignoring activation" }
      return true
    }

    // The catalogue row is needed here and not only at resolution: the cast time decides between the two
    // branches below, and a channelled cast has to carry the skill id into its Casting component.
    val skill = skillRepository.findByIdOrNull(msg.attackId)
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

    LOG.info { "Skill activated: ${skill.identifier} Lv. ${msg.skillLevel} at ${msg.targetPosition}" }

    // Before the cast starts and in its own scope, so a skill whose reagent is missing is refused while the
    // player is still looking at the button rather than after channelling for it. Nothing is spent here - see
    // SkillStrategy.checkCastStart.
    val denial = world.modify(activeEntityId) { id -> strategy.checkCastStart(this, id, msg.skillLevel) }
    if (denial != null) {
      LOG.debug { "Activation of ${skill.identifier} by $activeEntityId refused: $denial" }
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(denial))
      return true
    }

    // An entity-targeted skill carries a target id (the client sends 0 when nothing was picked); a
    // ground-targeted one falls back to the position, which is always present on the wire.
    val targetEntityId = msg.targetEntityId.takeIf { it != 0L }
    val targetPosition: Vec3L? = if (targetEntityId == null) msg.targetPosition else null

    val started = world.modify(activeEntityId) { id ->
      // Before the cast-time branch, deliberately: a message-handler context never runs nested inside
      // scheduler.tick(), so this add() applies immediately - unlike promoting only from
      // BattleContextFactory, which a channelled cast reaches from inside CastingSystem.update() and would
      // silently fizzle its first hit against a pristine prop. See PropPromotionService's own KDoc.
      if (targetEntityId != null) {
        propPromotion.promoteIfNeeded(this, targetEntityId)
      }

      if (skill.castTime > 0f) {
        // Starting a new cast supersedes whatever was being cast before.
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

      id
    } ?: return true

    // Outside the modify scope on purpose: resolution runs on a worker that has to take the world lock, and
    // handing it work while this thread still holds that lock would just make the worker wait.
    if (skill.castTime <= 0f) {
      skillExecutionService.execute(
        world = world,
        casterId = started,
        skillId = skill.id,
        skillLevel = msg.skillLevel,
        targetEntityId = targetEntityId,
        targetPosition = targetPosition
      )
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
