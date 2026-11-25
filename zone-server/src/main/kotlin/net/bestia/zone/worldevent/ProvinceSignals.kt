package net.bestia.zone.worldevent

import net.bestia.worldgen.place.Province
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaTier

/**
 * What the director knows about one province at one run: how thick the mana is and who is there. A snapshot,
 * so every event weighed in one run sees the same province.
 *
 * Only cells that hold a change are counted: an untouched cell is at its start density, which is calm almost
 * everywhere.
 */
data class ProvinceSignals(
  val province: Province,
  val isSanctuary: Boolean,
  /** The densest cell that holds a change, and its density. */
  val peakCell: Cell?,
  val peakDensity: Double,
  /** How many changed cells are at each tier from Stirring up. */
  val cellsAtTier: Map<ManaTier, Int>,
  /** Masters standing in the province right now. */
  val playersPresent: Int,
) {

  fun cellsAtLeast(tier: ManaTier): Int = cellsAtTier.filterKeys { it >= tier }.values.sum()
}
