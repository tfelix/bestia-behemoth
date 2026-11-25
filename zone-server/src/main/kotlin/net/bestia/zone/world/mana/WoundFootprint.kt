package net.bestia.zone.world.mana

import net.bestia.zone.world.mana.ManaField.Cell
import kotlin.math.sqrt

/**
 * The cells one Wound pours into, and each cell's share. The heart gets twice the share of the rim, so the land
 * around a Wound rises as a whole and its centre goes furthest.
 */
class WoundFootprint(centreX: Double, centreY: Double, reach: Double, field: ManaField) {

  val cells: List<Cell> = field.cellsWithin(centreX, centreY, reach)

  val shares: DoubleArray = run {
    val weights = DoubleArray(cells.size) { i ->
      val dx = cells[i].centreX - centreX
      val dy = cells[i].centreY - centreY
      1.0 - RIM_DROP * (sqrt(dx * dx + dy * dy) / reach).coerceAtMost(1.0)
    }
    val total = weights.sum()
    DoubleArray(weights.size) { weights[it] / total }
  }

  fun pour(field: ManaField, motes: Double) {
    cells.forEachIndexed { i, cell -> field.addToCell(cell, motes * shares[i]) }
  }

  private companion object {
    const val RIM_DROP = 0.5
  }
}
