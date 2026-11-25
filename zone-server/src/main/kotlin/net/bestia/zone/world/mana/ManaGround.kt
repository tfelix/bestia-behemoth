package net.bestia.zone.world.mana

import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.settlement.StartTownMarkets
import org.springframework.stereotype.Component

/** The land under the mana field, read from the generated world. */
@Component
class ManaGround(
  private val worldService: WorldService,
  private val startTowns: StartTownMarkets,
  private val config: ManaFieldConfig,
) : ManaFieldRule.Ground {

  fun potentialOf(cell: Cell): Double {
    return worldService.generated.materializer.surface.potentialAt(cell.centreX, cell.centreY)
  }

  override fun startDensity(cell: Cell): Double = StartDensity.of(potentialOf(cell))

  override fun isSanctuary(cell: Cell): Boolean {
    val reach = config.sanctuaryRadiusMetres

    return startTowns.startTownCentres().any { town ->
      val dx = cell.centreX - town.x
      val dy = cell.centreY - town.y
      dx * dx + dy * dy <= reach * reach
    }
  }
}
