package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Transmutation Workbench Kit (`items.yml` id 616), made in Handiwork with Carpentry. */
@Component
class TransmutationWorkbenchKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.TRANSMUTATION_WORKBENCH, BUILD_SECONDS) {

  override val itemId = 616L

  private companion object {
    /** As long as the glass furnace: a station is something you commit to. */
    const val BUILD_SECONDS = 20f
  }
}
