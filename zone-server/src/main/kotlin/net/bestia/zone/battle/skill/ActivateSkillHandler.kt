package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.ecs.battle.AvailableSkills
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.logout.LogoutCancelService
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
  private val world: WorldView,
  private val statusEffectService: StatusEffectService,
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

    // TEMPORARY: Heal (skills.yml id 4) doesn't have real damage/effect resolution wired up yet
    // (see class doc), so it's a convenient place to exercise the still-untested status effect sync
    // end-to-end - grants SWIFTNESS on cast. Self-targets when no target was picked, since Heal
    // is FRIENDLY-targeted. Replace with Heal's actual effect resolution once that lands.
    if (msg.attackId == HEAL_SKILL_ID) {
      val targetId = if (msg.targetEntityId != 0L) msg.targetEntityId else activeEntityId
      world.modify(targetId) { id ->
        statusEffectService.applyEffect(this, id, definitionId = SWIFTNESS_EFFECT_ID, level = msg.skillLevel, sourceEntityId = activeEntityId)
      }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val HEAL_SKILL_ID = 4L
    private const val SWIFTNESS_EFFECT_ID = 1L
  }
}
