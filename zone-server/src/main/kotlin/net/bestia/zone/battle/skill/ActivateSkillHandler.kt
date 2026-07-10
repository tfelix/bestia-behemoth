package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.AvailableSkills
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Handles a player activating a learned skill from the UI (Skills window or hotbar),
 * for whichever entity (master or an owned bestia) is currently active. Only validates
 * that the skill is known at the requested level and announces the activation to nearby
 * players - target selection and damage/effect resolution are handled elsewhere/later.
 */
@Component
class ActivateSkillHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<ActivateSkillCMSG> {
  override val handles = ActivateSkillCMSG::class

  override fun handle(msg: ActivateSkillCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // Resolve the skill knowledge inside a lock-held read scope so the component is never touched
    // (or mutated) off the tick thread. Returns null when the entity has no AvailableSkills at all.
    val knowsSkill = world.read {
      get(activeEntityId, AvailableSkills::class)?.knowsSkill(msg.attackId, msg.skillLevel)
    }

    if (knowsSkill == null) {
      LOG.warn { "Entity $activeEntityId does not have any available skill component" }
      return true
    }

    if (!knowsSkill) {
      LOG.warn { "Entity $activeEntityId does not know attack ${msg.attackId} at level ${msg.skillLevel}, ignoring activation" }
      return true
    }

    LOG.info { "Skill activated: ${msg.attackId} Lv. ${msg.skillLevel} at ${msg.targetPosition}" }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
