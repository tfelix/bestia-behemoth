package net.bestia.zone.battle.attack

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.AvailableSkills
import net.bestia.zone.ecs.core.World
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
  private val world: World
) : InMessageProcessor.IncomingMessageHandler<ActivateSkillCMSG> {
  override val handles = ActivateSkillCMSG::class

  override fun handle(msg: ActivateSkillCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    val knowsSkillComp = world.get(activeEntityId, AvailableSkills::class)

    if (knowsSkillComp == null) {
        LOG.warn { "Entity $activeEntityId does not have any available skill component" }
      return true
    }

    val knowsSkill = knowsSkillComp.knowsSkill(msg.attackId, msg.skillLevel)

    if (!knowsSkill) {
      LOG.warn { "Entity $activeEntityId does not know attack ${msg.attackId} at level ${msg.skillLevel}, ignoring activation" }
      return true
    }

    LOG.info { "Skill activated: ${msg.attackId} Lv. ${msg.skillLevel}" }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
