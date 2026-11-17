package net.bestia.zone.script

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/** Every [EntityScript] bean by its simple class name, the way `StatusEffectScriptRegistry` keys effects. */
@Component
class EntityScriptRegistry(scripts: List<EntityScript>) {

  private val byName: Map<String, EntityScript> = scripts
    .mapNotNull { script -> script::class.simpleName?.let { it to script } }
    .toMap()

  init {
    LOG.info { "Registered ${byName.size} entity script(s): ${byName.keys.sorted()}" }
  }

  /** Null for a script id that is no [EntityScript], like the ward stone's. */
  fun find(name: String): EntityScript? {
    return byName[name]
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
