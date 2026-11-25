package net.bestia.zone.world.mana

import net.bestia.worldgen.mana.StartDensity
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField.Cell
import org.springframework.stereotype.Component

/** The land under the mana field, read from the generated world. */
@Component
class ManaGround(
  private val worldService: WorldService,
  private val sanctuaries: ManaSanctuaries,
) : ManaFieldRule.Ground {

  fun potentialOf(cell: Cell): Double {
    return worldService.generated.materializer.surface.potentialAt(cell.centreX, cell.centreY)
  }

  override fun startDensity(cell: Cell): Double = StartDensity.of(potentialOf(cell))

  override fun isSanctuary(cell: Cell): Boolean = sanctuaries.isSanctuaryAt(cell.centreX, cell.centreY)
}
