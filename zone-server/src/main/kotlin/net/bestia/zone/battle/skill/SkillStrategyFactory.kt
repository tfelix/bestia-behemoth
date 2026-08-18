package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.skill.Skill
import org.springframework.stereotype.Component

/**
 * Resolves the `script` name from `skills.yml` (e.g. `Firebolt`) to the [SkillStrategy] bean
 * implementing it.
 *
 * Scripts are plain Spring beans in `net.bestia.zone.battle.skill.scripts`, keyed here by their
 * simple class name. This replaces an earlier `applicationContext.getBean(<fully qualified name>)`
 * lookup which could never have worked: it pointed at a package that does not exist
 * (`net.bestia.behemoth.battle.attack.scripts`), and `getBean(String)` resolves a *bean name*, which
 * for an annotated class is the decapitalised simple name rather than the FQN.
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
      ?: throw NoSkillScriptException()

    return get(skillScript)
      ?: throw IllegalStateException("No SkillStrategy bean registered for script '$skillScript'")
  }

  private fun get(scriptName: String): SkillStrategy? = byName[scriptName]

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
