package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Alchemy Laboratory Kit (`items.yml` id 614), made in Handiwork with Carpentry. */
@Component
class AlchemyLaboratoryKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.ALCHEMY_LABORATORY, BUILD_SECONDS) {

  override val itemId = 614L

  private companion object {
    /** As long as the glass furnace: a station is something you commit to. */
    const val BUILD_SECONDS = 20f
  }
}
