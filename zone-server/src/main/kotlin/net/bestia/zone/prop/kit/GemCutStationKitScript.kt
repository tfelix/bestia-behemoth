package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Gem Cut Station Kit (`items.yml` id 615), made in Handiwork with Carpentry. */
@Component
class GemCutStationKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.GEM_CUT_STATION, BUILD_SECONDS) {

  override val itemId = 615L

  private companion object {
    /** As long as the glass furnace: a station is something you commit to. */
    const val BUILD_SECONDS = 20f
  }
}
