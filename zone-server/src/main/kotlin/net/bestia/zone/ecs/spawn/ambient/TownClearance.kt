package net.bestia.zone.ecs.spawn.ambient

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind

/**
 * Whether a position is too close to a town for a creature to stand there.
 *
 * ### Measured from the buildings, not from the middle
 *
 * A radius around a settlement centre cannot express this. Towns are not round - `TownStage` lays streets
 * and plots along whatever shape the ground allows - and they differ by a factor of ten in size between a
 * hamlet and a city. One radius therefore either leaves creatures standing in a city's streets or empties
 * several square kilometres of open country around a hamlet.
 *
 * So the ring is measured from the **outermost building**. `FeatureKind.BUILDING` is a `FootprintFeature`
 * with a bounding box a few metres across, which makes "no creature within a hundred tiles of a house" a
 * question about real geometry, and it follows a ribbon town along a river exactly as it follows a compact
 * one.
 *
 * `TOWN_WALL` is deliberately not consulted, though it is the more obvious boundary. It is a `MarkerFeature`
 * carrying a `Polyline`, so its bounding box is a rectangle around a curved run - for a city that is a
 * square kilometre and a half of *outside* corners that would be emptied for no reason - and it is emitted
 * only over dry ground, so a coastal town's circuit has gaps. The buildings it encloses give the same
 * outline without either problem, and they exist at every tier, walls or no walls.
 *
 * ### Broad phase, then the real thing
 *
 * `SpawnerSystem`'s shape, for its reason. Nearly every site on the map is nowhere near a town - a city
 * keeps 55 km from the next - so a disc test against [StandingSettlements] answers those without touching
 * the feature index. Only a site inside some settlement's graded footprint plus the ring gets the exact
 * treatment, and each settlement's buildings are gathered once and kept.
 *
 * ### Why the kept form is a cell set
 *
 * Holding the boxes and testing each is O(buildings), and a city has thousands. Marking the lattice cells
 * they cover, widened by the ring, turns every later query into one hash probe. [MASK_CELL_METRES] rounds
 * the ring *up*, which is the safe direction for a keep-out zone and why the config calls the number
 * approximate.
 */
class TownClearance(
  private val generated: GeneratedWorld,
  private val settlements: StandingSettlements,
  private val clearanceTiles: Long
) {

  /** Dilated building cells per settlement, keyed on its centre cell. Filled the first time a player nears. */
  private val bySettlement = HashMap<Long, Set<Long>>()

  fun blocks(x: Long, y: Long): Boolean {
    val nearby = settlements.coveringWithin(x.toDouble(), y.toDouble(), clearanceTiles.toDouble())
    if (nearby.isEmpty()) return false

    val cell = cellOf(x, y)
    for (settlement in nearby) {
      if (cell in builtCellsOf(settlement)) return true
    }
    return false
  }

  /**
   * The cells this settlement's buildings cover, widened by the ring.
   *
   * One feature query per settlement for the life of the process. The box asked for is the graded footprint
   * plus the ring, which is guaranteed to contain every building the town has - `TownStage.builtRadiusFor`
   * caps the built radius below the footprint at every tier.
   */
  private fun builtCellsOf(settlement: StandingSettlements.Entry): Set<Long> {
    val key = cellOf(settlement.x.toLong(), settlement.y.toLong())
    bySettlement[key]?.let { return it }

    val reach = settlement.footprintRadius + clearanceTiles
    val area = Aabb(
      settlement.x - reach,
      settlement.y - reach,
      settlement.x + reach,
      settlement.y + reach
    )

    val dilation = Math.floorDiv(clearanceTiles + MASK_CELL_METRES - 1, MASK_CELL_METRES)
    val cells = HashSet<Long>()

    for (feature in generated.world.features.query(area)) {
      if (feature.kind != FeatureKind.BUILDING) continue

      val box = feature.bbox
      val firstX = Math.floorDiv(box.minX.toLong(), MASK_CELL_METRES) - dilation
      val lastX = Math.floorDiv(box.maxX.toLong(), MASK_CELL_METRES) + dilation
      val firstY = Math.floorDiv(box.minY.toLong(), MASK_CELL_METRES) - dilation
      val lastY = Math.floorDiv(box.maxY.toLong(), MASK_CELL_METRES) + dilation

      for (cy in firstY..lastY) {
        for (cx in firstX..lastX) {
          cells.add(AmbientSiteLattice.pack(cx, cy))
        }
      }
    }

    bySettlement[key] = cells
    return cells
  }

  private fun cellOf(x: Long, y: Long): Long {
    return AmbientSiteLattice.pack(
      Math.floorDiv(x, MASK_CELL_METRES),
      Math.floorDiv(y, MASK_CELL_METRES)
    )
  }

  companion object {
    /**
     * Edge of one mask cell, in metres.
     *
     * Positional and therefore a `const`, the warning `AreaNameRegistry.LATTICE_METRES` carries: the keys
     * mean nothing without it, so changing it reinterprets where every marked cell is. At 64 m a hundred-tile
     * ring is honoured as somewhere between 100 and 128.
     */
    const val MASK_CELL_METRES = 64L
  }
}
