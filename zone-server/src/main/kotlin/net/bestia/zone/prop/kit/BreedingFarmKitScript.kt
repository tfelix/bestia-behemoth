package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureService
import org.springframework.stereotype.Component

/** The Breeding Farm Kit (`items.yml` id 439), made at a Workbench with Carpentry. */
@Component
class BreedingFarmKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.BREEDING_FARM, BUILD_SECONDS) {

  override val itemId = 439L

  private companion object {
    /** A fence and a hut: more work than a bench. */
    const val BUILD_SECONDS = 30f
  }
}
