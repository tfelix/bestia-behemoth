package net.bestia.zone.battle.status

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Resolves the `script` name from `status_effects.yml` (e.g. `Swiftness`) to the
 * [StatusEffectScript] bean implementing it. Scripts are plain Spring beans in
 * `net.bestia.zone.battle.status.scripts`, keyed here by their simple class name - identical
 * shape to [net.bestia.zone.battle.skill.SkillScriptRegistry].
 */
@Component
class StatusEffectScriptRegistry(
  scripts: List<StatusEffectScript>
) {

  private val byName: Map<String, StatusEffectScript> = scripts
    .mapNotNull { script -> script::class.simpleName?.let { it to script } }
    .toMap()

  init {
    LOG.info { "Registered ${byName.size} status effect script(s): ${byName.keys.sorted()}" }
  }

  fun get(scriptName: String): StatusEffectScript? = byName[scriptName]

  fun getOrThrow(scriptName: String): StatusEffectScript =
    get(scriptName) ?: throw IllegalStateException("No StatusEffectScript registered under name '$scriptName'")

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
