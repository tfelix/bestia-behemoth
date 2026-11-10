package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Mana Circle Kit (`items.yml` id 325): laid out, it becomes the ring a ritual is held in. */
@Component
class ManaCircleKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.MANA_CIRCLE, BUILD_SECONDS) {

  override val itemId = 325L

  private companion object {
    /** Pressing a ring of paste into the ground and setting a stone: a short job. */
    const val BUILD_SECONDS = 5f
  }
}
