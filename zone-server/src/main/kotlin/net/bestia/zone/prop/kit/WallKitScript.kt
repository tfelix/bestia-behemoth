package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Wall Kit (`items.yml` id 106): a wall two metres wide and three high. */
@Component
class WallKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.STRUCTURE_WALL, BUILD_SECONDS, BUILDING_REACH) {

  override val itemId = 106L
}
