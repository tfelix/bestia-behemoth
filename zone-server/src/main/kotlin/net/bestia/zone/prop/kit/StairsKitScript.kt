package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Stairs Kit (`items.yml` id 108): a flight of stairs up one storey. */
@Component
class StairsKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.STRUCTURE_STAIRS, BUILD_SECONDS, BUILDING_REACH) {

  override val itemId = 108L
}
