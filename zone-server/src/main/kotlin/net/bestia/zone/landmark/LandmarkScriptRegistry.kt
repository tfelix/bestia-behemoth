package net.bestia.zone.landmark

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.poi.PoiKind
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.prop.PropLandmarkDto
import net.bestia.zone.util.CatalogValidator
import org.springframework.stereotype.Component

/** Resolves each landmark kind's configured script, the way `StatusEffectScriptRegistry` resolves effects. */
@Component
class LandmarkScriptRegistry(
  scripts: List<LandmarkScript>,
  private val kinds: PropKindRegistry
) : CatalogValidator {

  private val byName: Map<String, LandmarkScript> = scripts
    .mapNotNull { script -> script::class.simpleName?.let { it to script } }
    .toMap()

  init {
    LOG.info { "Registered ${byName.size} landmark script(s): ${byName.keys.sorted()}" }
  }

  /** The `prop-kinds.yml` row of [kind]. Every landmark kind has one; [validate] fails the boot otherwise. */
  fun configOf(kind: PoiKind): PropLandmarkDto {
    return checkNotNull(kinds.of(StaticEntityKind.ofPoi(kind)).landmark) { "$kind has no landmark block" }
  }

  fun scriptOf(kind: PoiKind): LandmarkScript {
    val name = configOf(kind).script
    return checkNotNull(byName[name]) { "No LandmarkScript registered under name '$name'" }
  }

  override fun validate() {
    for (kind in PoiKind.entries) {
      val staticKind = StaticEntityKind.ofPoi(kind)
      val config = checkNotNull(kinds.of(staticKind).landmark) {
        "prop-kinds.yml row $staticKind has no landmark block"
      }
      check(config.script in byName) {
        "prop-kinds.yml row $staticKind names landmark script ${config.script}, which is no LandmarkScript bean"
      }
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
