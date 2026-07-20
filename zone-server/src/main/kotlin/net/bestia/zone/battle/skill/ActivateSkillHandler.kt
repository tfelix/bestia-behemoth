package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.KnownSkills
import net.bestia.zone.ecs.battle.skill.Casting
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.logout.LogoutCancelService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Handles a player activating a learned skill from the UI (Skills window or hotbar), for whichever
 * entity (master or an owned bestia) is currently active.
 *
 * Validates that the skill is known at the requested level, then either resolves it immediately or -
 * when the skill has a cast time - attaches a [Casting] component and lets
 * [net.bestia.zone.ecs.battle.skill.CastingSystem] resolve it when the channel finishes.
 */
@Component
class ActivateSkillHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
  private val skillRepository: SkillRepository,
  private val skillExecutionService: SkillExecutionService,
  private val logoutCancelService: LogoutCancelService,
) : InMessageProcessor.IncomingMessageHandler<ActivateSkillCMSG> {
  override val handles = ActivateSkillCMSG::class

  override fun handle(msg: ActivateSkillCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // Using a skill is player activity — abort any pending logout.
    logoutCancelService.cancelLogout(activeEntityId)

    // Resolve the skill knowledge inside a lock-held read scope so the component is never touched
    // (or mutated) off the tick thread. Returns null when the entity has no AvailableSkills at all.
    val knowsSkill = world.read {
      get(activeEntityId, KnownSkills::class)?.knowsSkill(msg.attackId, msg.skillLevel)
    }

    if (knowsSkill == null) {
      LOG.warn { "Entity $activeEntityId does not have any available skill component" }
      return true
    }

    if (!knowsSkill) {
      LOG.warn { "Entity $activeEntityId does not know attack ${msg.attackId} at level ${msg.skillLevel}, ignoring activation" }
      return true
    }

    val skill = skillRepository.findById(msg.attackId).orElse(null)
    if (skill == null) {
      LOG.warn { "Entity $activeEntityId activated unknown skill ${msg.attackId}, ignoring" }
      return true
    }

    LOG.info { "Skill activated: ${msg.attackId} Lv. ${msg.skillLevel} at ${msg.targetPosition}" }

    // An entity-targeted skill carries a target id (the client sends 0 when nothing was picked); a
    // ground-targeted one falls back to the position, which is always present on the wire.
    val targetEntityId = msg.targetEntityId.takeIf { it != 0L }
    val targetPosition: Vec3L? = if (targetEntityId == null) msg.targetPosition else null

    world.modify(activeEntityId) { id ->
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
      } else {
        skillExecutionService.execute(
          world = this,
          casterId = id,
          skillId = skill.id,
          skillLevel = msg.skillLevel,
          targetEntityId = targetEntityId,
          targetPosition = targetPosition
        )
      }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
