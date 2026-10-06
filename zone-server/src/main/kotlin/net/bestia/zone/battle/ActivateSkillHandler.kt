package net.bestia.zone.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.battle.skill.NoSkillScriptException
import net.bestia.zone.battle.skill.SkillCheckService
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.battle.skill.SkillStrategyFactory
import net.bestia.zone.battle.skill.SkillTargetType
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.ecs.battle.skill.Casting
import net.bestia.zone.ecs.core.World
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
import net.bestia.zone.world.prop.PropPromotionService
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
  private val skillStrategyFactory: SkillStrategyFactory,
  private val skillExecutionService: SkillExecutionService,
  private val logoutCancelService: LogoutCancelService,
  private val deadActionGuard: DeadActionGuard,
  private val propPromotion: PropPromotionService,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<ActivateSkillCMSG> {
  override val wire = decoder(MessageCase.ACTIVATE_SKILL) { accountId, envelope ->
    ActivateSkillCMSG.fromBnet(accountId, envelope.activateSkill)
  }

  override fun handle(world: World, msg: ActivateSkillCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (deadActionGuard.refuses(world, activeEntityId, "activate a skill")) {
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

    LOG.info { "Skill activated: ${skill.identifier} Lv. ${msg.skillLevel} at ${msg.targetPosition}" }

    // Before the cast starts, so a skill whose reagent is missing is refused while the
    // player is still looking at the button rather than after channelling for it. Nothing is spent here - see
    // SkillStrategy.checkCastStart.
    val denial = world.modify(activeEntityId) { id -> strategy.checkCastStart(this, id, msg.skillLevel) }
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
        targetEntityId = pickedEntityId
        targetPosition = null
      }

      SkillTargetType.FRIENDLY -> {
        targetEntityId = pickedEntityId ?: activeEntityId
        targetPosition = null
      }
    }

    val started = world.modify(activeEntityId) { id ->
      // Before the cast-time branch, deliberately: a tick handler runs between two ticks, so this add()
      // applies immediately - unlike promoting only from
      // BattleContextFactory, which a channelled cast reaches from inside CastingSystem.update() and would
      // silently fizzle its first hit against a pristine prop. See PropPromotionService's own KDoc.
      val caster = get(id, Position::class)?.toVec3L()
      if (targetEntityId != null && caster != null) {
        propPromotion.promoteIfNeeded(this, targetEntityId, caster, PropPromotionService.TARGETING_REACH)
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
