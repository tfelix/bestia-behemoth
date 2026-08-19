package net.bestia.zone.battle.skill.scripts

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.SkillStrategyFactory
import net.bestia.zone.skill.SkillRepository
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Reports skills whose `skills.yml` `script` names no [net.bestia.zone.battle.skill.SkillStrategy]. Such a
 * skill can be learned and shown but does nothing when cast, so it is worth surfacing at boot.
 *
 * A skill with **no** `script` at all is not reported: that is the normal shape of a passive, and most of the
 * catalogue is one. A passive with a stat effect is not reported either, and must not name its
 * [net.bestia.zone.battle.skill.passive.PassiveSkillScript] here - that bean declares its own skill
 * identifier, which is what keeps this column meaning exactly one thing and lets `syncSkillDb` derive the
 * client's `is_passive` from it. A `script` naming a passive bean therefore shows up in this warning, which
 * is the intended way to notice the mistake.
 *
 * Runs on [ApplicationReadyEvent] rather than `@PostConstruct` because the `skill` table is only populated by
 * `SkillImporterBootRunner` (a `CommandLineRunner`), which runs *after* bean construction.
 *
 * This logs instead of throwing: the Survival, Scholar and Warrior trees are catalogued against a design that
 * is not refined yet, so most of their scripts do not exist, and a hard failure would make the server
 * unbootable against a populated database. The Novice, Craftsman and Blacksmith trees are complete, so
 * anything from those appearing in this warning is a regression.
 */
@Component
class SkillScriptBootValidator(
  private val skillRepository: SkillRepository,
  private val skillStrategyFactory: SkillStrategyFactory,
) {

  @EventListener(ApplicationReadyEvent::class)
  fun validateSkillScripts() {
    val missing = skillRepository.findAll()
      .mapNotNull { skill -> skill.script?.let { skill.identifier to it } }
      .filterNot { (_, script) -> skillStrategyFactory.has(script) }

    if (missing.isEmpty()) {
      return
    }

    LOG.warn {
      "No skill script found for ${missing.size} scripted skill(s); they can be learned but do nothing: " +
        missing.joinToString { (identifier, script) -> "$identifier -> $script" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
