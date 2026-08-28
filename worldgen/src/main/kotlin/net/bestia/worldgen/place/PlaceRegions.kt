package net.bestia.worldgen.place

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.World
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Vec2d

/**
 * The world divided into named areas, so a player can be told where they are without coordinates.
 *
 * ### Not a stage
 *
 * `climate/WeatherRegions` is the shape this copies, and its reasoning applies unchanged: nothing in
 * the pipeline consumes this, so making it a `Stage` would buy a `paramsVersion`, a place in the
 * version vector and a `pipelineVersion` move - which would invalidate every stored world - in
 * exchange for nothing. `civ/SettlementSpawnPoints` is the second precedent, and also returns rendered
 * names for `zone-server` alone.
 *
 * The cost is real and worth naming: reading layers off `GeneratedWorld` skips `LayerStore`'s
 * undeclared-read exception. What replaces it is structural rather than nothing - this runs after the
 * pipeline has frozen, so every layer it reads is final by construction, which is the condition that
 * exception exists to enforce.
 *
 * ### Rasterised, against `WeatherRegions`' advice
 *
 * That class argues against a region *layer* because a categorical boundary on a coarse grid is a
 * staircase, citing `voxel/SurfaceSampler.biomeAt`, which exists because a one-kilometre biome boundary
 * read as a drawn line. The objection is about a boundary being **drawn to a player**, and a place-name
 * boundary is not: the only observable is the metre at which a label changes, quantised to a kilometre,
 * which nobody can perceive.
 *
 * That holds only while the rule holds: **region boundaries are never rendered in the client or on a
 * served map tile.** `viewer/RegionOverlay` draws them deliberately, because judging the cost field
 * needs them visible - and if they are ever wanted in front of a player they need contouring and
 * smoothing first, not this grid.
 *
 * A nearest-seed `PointIndex` has no staircase at all, and is what this cannot use: the point of the
 * cost field is that a boundary lands on a ridge or a river rather than half way between two seeds, and
 * "nearest in cost" has no closed form to evaluate at a point.
 *
 * ### Coverage is the promise
 *
 * Every cell belongs to exactly one region, including open ocean and including an island too small to
 * have been seeded. `RegionGrowth.assign` guarantees that by construction and `pipeline/Invariants`
 * asserts it per seed. Anything less is a hole that only shows up when a player walks into it.
 */
class PlaceRegions private constructor(
  val regions: List<PlaceRegion>,
  private val owner: IntArray,
  private val grid: CellRegion,
  private val wrapX: Boolean,
  private val wrapY: Boolean
) {

  val count: Int
    get() {
      return regions.size
    }

  /** Edge of one partition cell, in metres. The granularity of every boundary. */
  val cellSize: Double
    get() {
      return grid.resolution.metresPerCell
    }

  /** How many regions hold any dry land. The number a designer would recognise as places. */
  val landCount: Int
    get() {
      return regions.count { !it.isWater }
    }

  /** Which region covers a world position, in metres. Any coordinate, normalised or not. */
  fun regionAt(worldX: Double, worldY: Double): PlaceRegion {
    return regions[indexAt(worldX, worldY)]
  }

  /**
   * Region index at a world position.
   *
   * Never negative: the partition is total, and a coordinate outside a non-wrapping axis clamps to the
   * edge cell rather than reporting nowhere. A caller asking about a position off the world has a bug,
   * but answering "the region at the edge" keeps that bug off a player's screen.
   */
  fun indexAt(worldX: Double, worldY: Double): Int {
    return ownerAt(owner, grid, wrapX, wrapY, worldX, worldY)
  }

  companion object {

    /**
     * Divides [world] into named regions.
     *
     * One Dijkstra plus a handful of linear passes over the base grid: sixteen thousand cells on the
     * genesis world, a quarter of a million on the demo one.
     *
     * Takes a [World] rather than a `GeneratedWorld` because everything it reads - layers, features, the
     * chronicle, the config - lives there. That also lets the viewer build one from a partial pipeline,
     * which is where a broken partition is easiest to look at.
     */
    fun of(world: World, params: RegionParams = RegionParams()): PlaceRegions {
      val config = world.config
      val layers = world.layers

      val elevation = layers.require<FloatLayer>(LayerId.ELEVATION)
      val biome = layers.require<IntLayer>(LayerId.BIOME)

      // Optional: a partial pipeline has no hydrology, and "no river boundaries" is the right answer
      // there rather than a failure. The same call WeatherRegions makes for mana.
      val discharge = layers[LayerId.DISCHARGE] as? FloatLayer

      val grid = elevation.region
      require(biome.region == grid) {
        "Growth indexes elevation and biome by the same cell, but they are on $grid and ${biome.region}"
      }

      val assignment = RegionGrowth.assign(
        grid = grid,
        elevation = elevation,
        biome = biome,
        discharge = discharge,
        worldSeed = config.seed,
        bounds = Aabb(0.0, 0.0, config.widthMetres, config.heightMetres),
        wrapX = config.wrapX,
        wrapY = config.wrapY,
        params = params
      )

      val merged = mergeSlivers(assignment, grid, config.wrapX, config.wrapY, params.minCells)
      val stats = summarise(merged, grid, elevation, biome, config.wrapX, config.wrapY)

      val kinds = stats.map {
        RegionKind.of(it.dominantBiome, it.relief, it.landShare, it.coastalShare, it.meanElevation)
      }
      val named = RegionNaming.nameAll(
        world = world,
        count = merged.regionCount,
        kinds = kinds,
        neighbours = neighboursOf(merged, grid, config.wrapX, config.wrapY),
        regionOf = { x, y -> ownerAt(merged.owner, grid, config.wrapX, config.wrapY, x, y) }
      )

      val regions = (0 until merged.regionCount).map { region ->
        PlaceRegion(
          index = region,
          seed = merged.seeds[region],
          centre = stats[region].centre,
          cellCount = stats[region].cellCount,
          kind = kinds[region],
          name = named[region].name,
          nameSeed = named[region].nameSeed,
          cultureIndex = named[region].cultureIndex,
          isWater = merged.isWater[region],
          landShare = stats[region].landShare,
          meanElevation = stats[region].meanElevation,
          relief = stats[region].relief,
          dominantBiome = stats[region].dominantBiome
        )
      }

      return PlaceRegions(regions, merged.owner, grid, config.wrapX, config.wrapY)
    }

    /**
     * Which regions each region touches.
     *
     * Only used to scope name uniqueness, so the set is kept rather than the border length: two regions
     * meeting at all is enough reason for a player to need to tell them apart.
     */
    private fun neighboursOf(
      assignment: RegionGrowth.Assignment,
      grid: CellRegion,
      wrapX: Boolean,
      wrapY: Boolean
    ): List<IntArray> {
      val adjacent = List(assignment.regionCount) { HashSet<Int>() }

      for (cell in assignment.owner.indices) {
        val here = assignment.owner[cell]
        for (direction in CARDINALS) {
          val next = step(cell, direction, grid, wrapX, wrapY) ?: continue
          val there = assignment.owner[next]
          if (there != here) adjacent[here].add(there)
        }
      }

      // Sorted so the naming pass sees neighbours in a fixed order, and so two runs of one world cannot
      // resolve the same clash differently.
      return adjacent.map { it.sorted().toIntArray() }
    }

    /**
     * Which region owns a world position.
     *
     * Shared by [indexAt] and the naming pass, which needs the same lookup before the object it belongs
     * to exists. Two copies of coordinate folding is exactly the kind of thing that drifts apart and
     * then disagrees about one cell at the seam.
     */
    private fun ownerAt(
      owner: IntArray,
      grid: CellRegion,
      wrapX: Boolean,
      wrapY: Boolean,
      worldX: Double,
      worldY: Double
    ): Int {
      val metres = grid.resolution.metresPerCell
      val rawX = Math.floor(worldX / metres).toInt() - grid.minX
      val rawY = Math.floor(worldY / metres).toInt() - grid.minY

      val lx = if (wrapX) Math.floorMod(rawX, grid.width) else rawX.coerceIn(0, grid.width - 1)
      val ly = if (wrapY) Math.floorMod(rawY, grid.height) else rawY.coerceIn(0, grid.height - 1)

      return owner[ly * grid.width + lx]
    }

    /**
     * Absorbs regions below [minCells] into whichever neighbour they share the most border with.
     *
     * A sliver is worse than no region at all: it takes a name out of the pool shared with the ground
     * around it, and a player crossing it sees a label flicker on for two steps. Only a region with no
     * neighbour of its own water class survives undersized - that is an island or a pond, and an island
     * genuinely is a place.
     *
     * Border counts go in a map keyed on the packed pair rather than a matrix, because a matrix is
     * quadratic in the region count and the demo world has five thousand of them - twenty-five million
     * cells for a table that is almost entirely zero. Only cells belonging to a sliver are examined.
     */
    private fun mergeSlivers(
      assignment: RegionGrowth.Assignment,
      grid: CellRegion,
      wrapX: Boolean,
      wrapY: Boolean,
      minCells: Int
    ): RegionGrowth.Assignment {
      val counts = IntArray(assignment.regionCount)
      for (region in assignment.owner) counts[region]++

      val slivers = counts.indices.filter { counts[it] in 1 until minCells }.toHashSet()
      if (slivers.isEmpty()) return assignment

      val borders = HashMap<Long, Int>()

      for (cell in assignment.owner.indices) {
        val here = assignment.owner[cell]
        if (here !in slivers) continue

        for (direction in CARDINALS) {
          val next = step(cell, direction, grid, wrapX, wrapY) ?: continue
          val there = assignment.owner[next]
          if (there == here || assignment.isWater[there] != assignment.isWater[here]) continue

          val key = (here.toLong() shl 32) or (there.toLong() and 0xFFFFFFFFL)
          borders[key] = (borders[key] ?: 0) + 1
        }
      }

      val absorbInto = IntArray(assignment.regionCount) { -1 }
      for (sliver in slivers.sorted()) {
        var best = -1
        var bestBorder = 0
        for ((key, shared) in borders) {
          if ((key ushr 32).toInt() != sliver) continue
          val candidate = (key and 0xFFFFFFFFL).toInt()

          // Tie-break on the lower index, or the result depends on map iteration order.
          val better = shared > bestBorder || (shared == bestBorder && best >= 0 && candidate < best)
          if (better) {
            best = candidate
            bestBorder = shared
          }
        }
        absorbInto[sliver] = best
      }

      return remap(assignment, absorbInto, counts)
    }

    /**
     * Rewrites an assignment so surviving regions are densely indexed from zero.
     *
     * Absorption chains - a sliver's best neighbour may itself be a sliver - so each target is followed
     * to a survivor, bounded by the region count in case two slivers pick each other. A cycle keeps
     * both, which is the safe failure: an extra small region is cosmetic, a region pointing at nothing
     * is a crash on the next lookup.
     */
    private fun remap(
      assignment: RegionGrowth.Assignment,
      absorbInto: IntArray,
      counts: IntArray
    ): RegionGrowth.Assignment {
      val resolved = IntArray(assignment.regionCount) { it }
      for (region in resolved.indices) {
        var target = region
        var hops = 0
        while (absorbInto[target] >= 0 && hops < assignment.regionCount) {
          target = absorbInto[target]
          hops++
        }
        resolved[region] = if (absorbInto[target] >= 0) region else target
      }

      val dense = IntArray(assignment.regionCount) { -1 }
      val seeds = ArrayList<Vec2d>()
      val water = ArrayList<Boolean>()

      for (region in resolved.indices) {
        if (resolved[region] != region || counts[region] == 0) continue
        dense[region] = seeds.size
        seeds.add(assignment.seeds[region])
        water.add(assignment.isWater[region])
      }

      val owner = IntArray(assignment.owner.size) { dense[resolved[assignment.owner[it]]] }
      require(owner.none { it < 0 }) { "region merge left a cell pointing at an absorbed region" }

      return RegionGrowth.Assignment(
        owner = owner,
        regionCount = seeds.size,
        seeds = seeds,
        isWater = BooleanArray(water.size) { water[it] }
      )
    }

    /**
     * Per-region character, in one pass over the grid.
     *
     * Land is counted off the **biome** water classification rather than off elevation against sea
     * level, because that is what `RegionGrowth` partitioned on. Elevation would disagree for a lake
     * above sea level: every cell of it reads as land, the region comes back with a land share of one,
     * and [RegionKind.of] names a lake after a meadow.
     */
    private fun summarise(
      assignment: RegionGrowth.Assignment,
      grid: CellRegion,
      elevation: FloatLayer,
      biome: IntLayer,
      wrapX: Boolean,
      wrapY: Boolean
    ): List<RegionStats> {
      val count = assignment.regionCount
      val cells = IntArray(count)
      for (region in assignment.owner) cells[region]++

      // One flat array with a slice per region rather than a list per region: the total is the cell
      // count either way, and this way no elevation is boxed.
      val offsets = IntArray(count + 1)
      for (region in 0 until count) offsets[region + 1] = offsets[region] + cells[region]
      val heights = DoubleArray(assignment.owner.size)
      val cursor = offsets.copyOf()

      val sumX = DoubleArray(count)
      val sumY = DoubleArray(count)
      val land = IntArray(count)
      val coastal = IntArray(count)
      val biomeCounts = IntArray(count * Biome.entries.size)
      val metres = grid.resolution.metresPerCell

      for (cell in assignment.owner.indices) {
        val region = assignment.owner[cell]
        val cellX = grid.minX + cell % grid.width
        val cellY = grid.minY + cell / grid.width

        sumX[region] += (cellX + 0.5) * metres
        sumY[region] += (cellY + 0.5) * metres

        heights[cursor[region]++] = elevation[cellX, cellY].toDouble()

        val classified = biome[cellX, cellY]
        val dry = !Biome.entries[classified].isWater
        if (dry) land[region]++
        biomeCounts[region * Biome.entries.size + classified]++

        if (dry && touchesWater(cell, grid, biome, wrapX, wrapY)) coastal[region]++
      }

      return (0 until count).map { region ->
        val size = cells[region].coerceAtLeast(1)
        val slice = heights.copyOfRange(offsets[region], offsets[region + 1])
        slice.sort()

        var dominant = 0
        for (candidate in Biome.entries.indices) {
          if (biomeCounts[region * Biome.entries.size + candidate] >
            biomeCounts[region * Biome.entries.size + dominant]
          ) {
            dominant = candidate
          }
        }

        RegionStats(
          centre = Vec2d(sumX[region] / size, sumY[region] / size),
          cellCount = cells[region],
          landShare = land[region].toDouble() / size,
          coastalShare = coastal[region].toDouble() / size,
          meanElevation = if (slice.isEmpty()) 0.0 else slice.average(),
          relief = percentile(slice, 0.95) - percentile(slice, 0.05),
          dominantBiome = Biome.entries[dominant]
        )
      }
    }

    private fun percentile(sorted: DoubleArray, share: Double): Double {
      if (sorted.isEmpty()) return 0.0
      val at = (share * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
      return sorted[at]
    }

    /**
     * Whether a cell has water beside it.
     *
     * How a coast is recognised. The land share cannot do it: `RegionGrowth` refuses to cross a
     * coastline, so every land region is entirely land by construction and a share-based test is dead
     * code - which it was, until this replaced it.
     */
    private fun touchesWater(
      cell: Int,
      grid: CellRegion,
      biome: IntLayer,
      wrapX: Boolean,
      wrapY: Boolean
    ): Boolean {
      for (direction in CARDINALS) {
        val next = step(cell, direction, grid, wrapX, wrapY) ?: continue
        val classified = biome[grid.minX + next % grid.width, grid.minY + next / grid.width]
        if (Biome.entries[classified].isWater) return true
      }
      return false
    }

    /** The cell one cardinal step from [cell], or null where a non-wrapping world ends. */
    private fun step(
      cell: Int,
      direction: Int,
      grid: CellRegion,
      wrapX: Boolean,
      wrapY: Boolean
    ): Int? {
      val x = fold(cell % grid.width + D8.DX[direction], grid.width, wrapX) ?: return null
      val y = fold(cell / grid.width + D8.DY[direction], grid.height, wrapY) ?: return null
      return y * grid.width + x
    }

    private fun fold(value: Int, extent: Int, wraps: Boolean): Int? {
      if (wraps) return Math.floorMod(value, extent)
      if (value < 0 || value >= extent) return null
      return value
    }

    /** East, north, west, south - the even entries of [D8]'s contractual order. */
    private val CARDINALS = intArrayOf(0, 2, 4, 6)

    /** What the partition knows about a region before anybody has named it. */
    private class RegionStats(
      val centre: Vec2d,
      val cellCount: Int,
      val landShare: Double,
      /** Share of the region's cells that are dry land with water next to them. */
      val coastalShare: Double,
      val meanElevation: Double,
      val relief: Double,
      val dominantBiome: Biome
    )
  }
}
