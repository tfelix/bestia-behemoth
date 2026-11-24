package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Carving Table Kit (`items.yml` id 640), made in Handiwork with Runic Etching. */
@Component
class CarvingTableKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.CARVING_TABLE, BUILD_SECONDS) {

  override val itemId = 640L

  private companion object {
    /** As long as the gem cut station it stands next to in the tree. */
    const val BUILD_SECONDS = 20f
  }
}
