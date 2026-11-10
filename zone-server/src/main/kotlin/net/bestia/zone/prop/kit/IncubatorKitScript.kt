package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Incubator Kit (`items.yml` id 442), made at a Workbench with Carpentry. */
@Component
class IncubatorKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.INCUBATOR, BUILD_SECONDS) {

  override val itemId = 442L

  private companion object {
    /** Fired clay over an ember core: as much work as a furnace. */
    const val BUILD_SECONDS = 25f
  }
}
