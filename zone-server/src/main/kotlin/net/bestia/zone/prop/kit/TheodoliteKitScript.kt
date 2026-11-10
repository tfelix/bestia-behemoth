package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Theodolite Kit (`items.yml` id 326): set up on open ground, it is where Cartography surveys. */
@Component
class TheodoliteKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.THEODOLITE, BUILD_SECONDS) {

  override val itemId = 326L

  private companion object {
    const val BUILD_SECONDS = 5f
  }
}
