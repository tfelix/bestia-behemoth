package net.bestia.worldgen.hydro

import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.IntGrid

/**
 * One closed basin, and the water level it settles at.
 *
 * The distinction between the two levels is the whole content of this type. [spillLevel] is where the
 * basin would overflow - the saddle Priority-Flood raised it to. [surfaceLevel] is where the water
 * actually stands, which for a basin in a dry climate is lower, because evaporation from the lake
 * surface removes water as fast as the rivers deliver it.
 */
data class Basin(
  val id: Int,
  /** Every cell of the depression, ascending, so basin identity never depends on traversal order. */
  val cells: IntArray,
  /**
   * The subset actually under water, lowest first.
   *
   * Separate from [cells] because an endorheic basin does not fill to its rim: the cells above the water
   * line belong to the depression but are dry ground, and treating them as lake bed would put a shoreline
   * where there is none.
   */
  val floodedCells: IntArray,
  val spillLevel: Double,
  val surfaceLevel: Double,
  /** Total inflow in cubic metres per second. */
  val inflow: Double,
  /**
   * True when the basin never reaches its spill point: a terminal lake with no outlet, which concentrates
   * salt because the only way out is evaporation.
   */
  val endorheic: Boolean
) {
  val area get() = floodedCells.size

  // Data classes with array members need these written out; the default ones compare by identity.
  override fun equals(other: Any?) = this === other || (other is Basin && id == other.id)
  override fun hashCode() = id
}

/** Where standing water is, how deep, and whether it has an outlet. */
class LakeMap(
  /** 0 where there is no lake, positive for an outlet lake, negative for an endorheic one. */
  val lakeId: IntGrid,
  /** Water surface elevation, or [Double.NaN] where there is none. Sea included. */
  val surface: Grid,
  val basins: List<Basin>
) {
  val lakeCount get() = basins.size
  val endorheicCount get() = basins.count { it.endorheic }
}

/**
 * Lake extraction from a solved drainage network.
 *
 * Priority-Flood has already done the hard part: any cell it had to raise was sitting in a depression,
 * and the amount it was raised by is how deep that depression is. What is left is to group those cells
 * into basins, decide whether each one overflows or evaporates away, and set the water level.
 *
 * The endorheic case is worth the extra work. A basin filled to its rim always spills, which means every
 * lake in the world has an outlet river and there is no such thing as a salt lake - no Caspian, no Great
 * Basin, no Dead Sea. Those are distinctive places, and they fall out of one evaporation balance.
 */
object Lakes {

  /** Seconds in a year, for turning a discharge in cubic metres per second into an annual volume. */
  const val SECONDS_PER_YEAR = 31_557_600.0

  /**
   * @param fillDepthThreshold minimum fill depth in metres for a cell to count as flooded. Filters out
   *   the epsilon gradient Priority-Flood imposes on flats, which is a numerical device and not a lake.
   * @param evaporationDepth metres of water evaporated from a lake surface per year. Around 0.7 in a
   *   temperate climate and over 2 in a desert; a single figure is enough to separate the two regimes.
   */
  fun identify(
    network: DrainageNetwork,
    elevation: Grid,
    discharge: Grid,
    seaLevel: Double,
    fillDepthThreshold: Double = 0.5,
    evaporationDepth: Double = 1.1
  ): LakeMap {
    val width = network.width
    val height = network.height
    val cellArea = network.metresPerCell * network.metresPerCell

    val lakeId = IntGrid(width, height)
    val surface = Grid(width, height, Double.NaN)
    val basins = ArrayList<Basin>()

    val flooded = BooleanArray(network.size) { i ->
      !network.ocean[i] && network.fillDepth.data[i] > fillDepthThreshold
    }

    val component = IntArray(network.size) { -1 }
    var nextId = 1

    // Scan in index order so basin ids are a function of position rather than of discovery order.
    for (start in 0 until network.size) {
      if (!flooded[start] || component[start] >= 0) continue

      val cells = ArrayList<Int>()
      val queue = ArrayDeque<Int>()
      component[start] = nextId
      queue.addLast(start)

      while (queue.isNotEmpty()) {
        val i = queue.removeFirst()
        cells.add(i)

        val x = i % width
        val y = i / width
        for (d in 0 until 8) {
          val nx = x + D8.DX[d]
          val ny = y + D8.DY[d]
          if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
          val n = ny * width + nx
          if (!flooded[n] || component[n] >= 0) continue
          component[n] = nextId
          queue.addLast(n)
        }
      }

      cells.sort()
      basins.add(measure(nextId, cells.toIntArray(), network, elevation, discharge, cellArea, evaporationDepth))
      nextId++
    }

    // Only the cells the water actually reaches: a basin that evaporated down to half its depth leaves
    // its upper shoreline dry, and those cells must not read as lake bed. Driven by the flooded-cell list
    // rather than by comparing each cell against the level, because a basin with a dead flat floor has
    // every cell at exactly the water level and a comparison floods either all of them or none.
    for (basin in basins) {
      val signedId = if (basin.endorheic) -basin.id else basin.id
      for (i in basin.floodedCells) {
        lakeId.data[i] = signedId
        surface.data[i] = basin.surfaceLevel
      }
    }

    for (i in network.ocean.indices) {
      if (network.ocean[i]) surface.data[i] = seaLevel
    }

    return LakeMap(lakeId, surface, basins)
  }

  /**
   * Settles one basin: how many of its cells are under water, and at what level.
   *
   * Inflow against evaporation. A lake grows until its surface is large enough that evaporation from it
   * matches the rivers arriving. Because the basin's cells sorted by elevation *are* its hypsometric
   * curve, the balance point is found by counting how many cells' worth of surface the inflow can
   * sustain - one division, no iteration.
   *
   * Deciding by *rank* rather than by an elevation threshold is what makes this robust. A basin with a
   * dead flat floor - which is common, because Priority-Flood produces them - has every cell at exactly
   * one elevation, and any threshold either floods all of them or none.
   */
  private fun measure(
    id: Int,
    cells: IntArray,
    network: DrainageNetwork,
    elevation: Grid,
    discharge: Grid,
    cellArea: Double,
    evaporationDepth: Double
  ): Basin {
    var spillLevel = Double.NEGATIVE_INFINITY
    var inflow = 0.0

    for (i in cells) {
      // The filled surface is flat across a basin at its spill elevation, bar the epsilon gradient.
      if (network.filled.data[i] > spillLevel) spillLevel = network.filled.data[i]
      // Discharge is cumulative, so the largest value in the basin is everything arriving in it.
      if (discharge.data[i] > inflow) inflow = discharge.data[i]
    }

    val byElevation = cells.sortedBy { elevation.data[it] }
    val sustainableCells = (inflow * SECONDS_PER_YEAR / evaporationDepth / cellArea).toInt()
    val endorheic = sustainableCells < cells.size

    // At least one cell, so a depression with any inflow at all holds a pond rather than being reported
    // as a lake with no water in it.
    val floodedCount = if (endorheic) sustainableCells.coerceIn(1, cells.size) else cells.size
    val flooded = IntArray(floodedCount) { byElevation[it] }

    val surfaceLevel = if (!endorheic) {
      spillLevel
    } else {
      // High enough to cover every flooded cell, and no higher than the first dry one.
      val highestFlooded = elevation.data[flooded[floodedCount - 1]]
      val firstDry = if (floodedCount < cells.size) {
        elevation.data[byElevation[floodedCount]]
      } else {
        spillLevel
      }
      maxOf(firstDry, highestFlooded + MIN_LAKE_DEPTH).coerceAtMost(spillLevel)
    }

    return Basin(
      id = id,
      cells = cells,
      floodedCells = flooded,
      spillLevel = spillLevel,
      surfaceLevel = surfaceLevel,
      inflow = inflow,
      endorheic = endorheic
    )
  }

  /** A lake is at least this deep at its lowest point, in metres. Below that it is a mudflat. */
  private const val MIN_LAKE_DEPTH = 0.5
}
