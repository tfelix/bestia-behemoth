package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Floor Kit (`items.yml` id 107): a floor two metres square. */
@Component
class FloorKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.STRUCTURE_FLOOR, BUILD_SECONDS, BUILDING_REACH) {

  override val itemId = 107L
}
