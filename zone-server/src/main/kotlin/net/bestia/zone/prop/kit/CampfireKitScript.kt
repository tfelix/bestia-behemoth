package net.bestia.zone.prop.kit

import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.entity.StaticEntityKind
import org.springframework.stereotype.Component

/** The Campfire Kit (`items.yml` id 126), made in Handiwork with Cooking. */
@Component
class CampfireKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.CAMPFIRE, BUILD_SECONDS) {

  override val itemId = 126L

  private companion object {
    /** Stones in a ring and wood on top: a moment's work, since it burns down within the hour. */
    const val BUILD_SECONDS = 3f
  }
}
