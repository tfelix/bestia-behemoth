package net.bestia.zone.battle.skill.scripts

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.battle.skill.SkillScriptRegistry
import net.bestia.zone.battle.skill.AttackType
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Reports skills whose `skills.yml` `script` has no matching [net.bestia.zone.battle.skill.SkillStrategy]
 * bean - casting such a skill fails at resolution time, so it is worth surfacing at boot.
 *
 * Runs on [ApplicationReadyEvent] rather than `@PostConstruct` because the `skill` table is only
 * populated by `SkillImporterBootRunner` (a `CommandLineRunner`), which runs *after* bean
 * construction. The old `@PostConstruct` version always saw an empty table and so never checked
 * anything - which is the only reason it passed, since it also looked scripts up under a package that
 * does not exist.
 *
 * This logs instead of throwing: the Survival, Scholar and Warrior trees are catalogued in `skills.yml`
 * against a design that is not refined yet, so most of their scripts do not exist, and a hard failure
 * would make the server unbootable against a populated database. The Novice, Craftsman and Blacksmith
 * trees are complete, so anything from those appearing in this warning is a regression.
 */
@Component
class SkillScriptBootValidator(
  private val skillRepository: SkillRepository,
  private val skillScriptRegistry: SkillScriptRegistry
) {

  @EventListener(ApplicationReadyEvent::class)
  fun validateSkillScripts() {
    val missing = skillRepository.findAll()
      // PASSIVE skills are always-on and never resolved through a strategy.
      .filter { it.type != AttackType.PASSIVE }
      .mapNotNull { skill -> skill.script?.let { skill.identifier to it } }
      .filterNot { (_, script) -> skillScriptRegistry.has(script) }

    if (missing.isEmpty()) {
      return
    }

    LOG.warn {
      "No SkillStrategy bean found for ${missing.size} scripted skill(s); activating them will fail: " +
        missing.joinToString { (identifier, script) -> "$identifier -> $script" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
