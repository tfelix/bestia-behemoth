package net.bestia.zone.prop.kit

import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.entity.StaticEntityKind
import org.springframework.stereotype.Component

/** The Glass Furnace Kit (`items.yml` id 254), made in Handiwork with Carpentry. */
@Component
class GlassFurnaceKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.GLASS_FURNACE, BUILD_SECONDS) {

  override val itemId = 254L

  private companion object {
    /** Long enough that a build is something you commit to, short enough to stand through. */
    const val BUILD_SECONDS = 20f
  }
}
