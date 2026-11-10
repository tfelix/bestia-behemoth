package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Chest Kit (`items.yml` id 127), made in Handiwork with Carpentry. */
@Component
class ChestKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.CHEST, BUILD_SECONDS) {

  override val itemId = 127L

  private companion object {
    /** A box with a lid: quicker than a workbench. */
    const val BUILD_SECONDS = 10f
  }
}
