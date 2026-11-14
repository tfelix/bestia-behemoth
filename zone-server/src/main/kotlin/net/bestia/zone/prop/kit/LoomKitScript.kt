package net.bestia.zone.prop.kit

import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.entity.StaticEntityKind
import org.springframework.stereotype.Component

/** The Loom Kit (`items.yml` id 253), made in Handiwork with Carpentry. */
@Component
class LoomKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.LOOM, BUILD_SECONDS) {

  override val itemId = 253L

  private companion object {
    /** Long enough that a build is something you commit to, short enough to stand through. */
    const val BUILD_SECONDS = 20f
  }
}
