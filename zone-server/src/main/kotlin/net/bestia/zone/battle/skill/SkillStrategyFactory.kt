package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.skill.Skill
import org.springframework.stereotype.Component

/**
 * Resolves the `script` name from `skills.yml` (e.g. `Firebolt`) to the [SkillStrategy] bean implementing
 * it. Scripts are plain Spring beans in `net.bestia.zone.battle.skill.scripts`, keyed by their simple class
 * name.
 *
 * This registry is also the answer to "may this skill be cast at all", which is why `Skill` no longer
 * carries a type: a skill with a strategy is active, and one without is a passive or an unfinished entry
 * that the client must offer no way to activate. See [has].
 */
@Component
class SkillStrategyFactory(
  scripts: List<SkillStrategy>,
) {

  private val byName: Map<String, SkillStrategy> = scripts
    .mapNotNull { script -> script::class.simpleName?.let { it to script } }
    .toMap()

  init {
    LOG.info { "Registered ${byName.size} skill script(s): ${byName.keys.sorted()}" }
  }

  fun getSkillStrategy(skill: Skill): SkillStrategy {
    val skillScript = skill.script
      ?: throw NoSkillScriptException(skill.identifier)

    return byName[skillScript]
      ?: throw NoSkillScriptException(skill.identifier)
  }

  /** Whether [scriptName] has an implementation, i.e. whether the skill naming it can be cast. */
  fun has(scriptName: String): Boolean = byName.containsKey(scriptName)

  fun isCastable(skill: Skill): Boolean = skill.script?.let { has(it) } == true

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
