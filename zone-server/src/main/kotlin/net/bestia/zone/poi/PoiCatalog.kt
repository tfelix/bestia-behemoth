package net.bestia.zone.poi

import net.bestia.worldgen.poi.PoiKind
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.prop.PropPoiDto
import net.bestia.zone.script.EntityScriptRegistry
import net.bestia.zone.util.CatalogValidator
import org.springframework.stereotype.Component

/** The `poi` block of each POI kind's `prop-kinds.yml` row. */
@Component
class PoiCatalog(
  private val kinds: PropKindRegistry,
  private val scripts: EntityScriptRegistry,
) : CatalogValidator {

  /** Every POI kind has one; [validate] fails the boot otherwise. */
  fun configOf(kind: PoiKind): PropPoiDto {
    return checkNotNull(kinds.of(StaticEntityKind.ofPoi(kind)).poi) { "$kind has no poi block" }
  }

  override fun validate() {
    for (kind in PoiKind.entries) {
      val staticKind = StaticEntityKind.ofPoi(kind)
      val config = checkNotNull(kinds.of(staticKind).poi) { "prop-kinds.yml row $staticKind has no poi block" }
      checkNotNull(scripts.find(config.script)) {
        "prop-kinds.yml row $staticKind names script ${config.script}, which is no EntityScript bean"
      }
    }
  }
}
