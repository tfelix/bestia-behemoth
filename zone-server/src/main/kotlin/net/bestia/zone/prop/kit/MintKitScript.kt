package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Mint Kit (`items.yml` id 645), made in Handiwork with Minting. */
@Component
class MintKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.MINT, BUILD_SECONDS) {

  override val itemId = 645L

  private companion object {
    /** As long as the other stations: a station is something you commit to. */
    const val BUILD_SECONDS = 20f
  }
}
