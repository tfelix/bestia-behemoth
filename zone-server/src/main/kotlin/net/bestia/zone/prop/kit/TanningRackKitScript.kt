package net.bestia.zone.prop.kit

import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.entity.StaticEntityKind
import org.springframework.stereotype.Component

/** The Tanning Rack Kit (`items.yml` id 252), made in Handiwork with Carpentry. */
@Component
class TanningRackKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.TANNING_RACK, BUILD_SECONDS) {

  override val itemId = 252L

  private companion object {
    /** Long enough that a build is something you commit to, short enough to stand through. */
    const val BUILD_SECONDS = 15f
  }
}
