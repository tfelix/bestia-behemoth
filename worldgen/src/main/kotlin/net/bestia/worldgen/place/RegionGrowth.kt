package net.bestia.worldgen.place

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.DoubleIntHeap
import net.bestia.worldgen.fields.PoissonDisk
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs

/**
 * Which region every cell of the base grid belongs to.
 *
 * A cost-weighted multi-source Dijkstra from blue-noise seed points: a cell belongs to the seed that
 * is nearest **in cost**, not in distance. With every weight at zero this is exactly Voronoi; with the
 * shipped weights the boundaries settle onto biome edges, ridges, rivers and coasts, which is where a
 * person would put them.
 *
 * ### Four neighbours, not eight
 *
 * `D8` is reused for the offsets so there is no second copy of them, but only the four cardinals are
 * stepped. A river or a coast is one cell wide in the raster, and a diagonal step hops straight over a
 * one-cell barrier - so an eight-neighbour growth leaks across exactly the features the cost field
 * exists to respect. The price is boundaries that prefer axis-aligned staircases at the pixel level,
 * which nothing renders (see [PlaceRegions]).
 *
 * ### The coastline is a wall, not a penalty
 *
 * A step between a water cell and a land cell is refused outright rather than charged for. A name that
 * covers both a bay and the mountain above it is not a name, and no finite penalty guarantees that
 * never happens - a wide enough peninsula makes crossing the water cheaper than going round.
 *
 * The cost is that an island or a lake with no seed of its own is unreachable, which is a **coverage**
 * failure and coverage is the whole promise. [assign] closes that itself: every cell left unclaimed
 * seeds a fresh region and floods its own component, so the partition is total by construction rather
 * than by the seeding happening to work out.
 */
object RegionGrowth {

  /**
   * The assignment, plus the per-region facts only the growth knows.
   *
   * `owner` is indexed by local cell (`ly * width + lx`), never by world cell, because the arrays are
   * sized to the grid rather than to the world's coordinate origin.
   */
  class Assignment(
    val owner: IntArray,
    val regionCount: Int,
    val seeds: List<Vec2d>,
    /** Per region, whether it grew in water. Never mixed - the coastline is a wall. */
    val isWater: BooleanArray
  )

  private const val SEED_SALT = 0x506C616365L

  /** Separates the sea's point set from the land's, so the two spacings cannot correlate. */
  private const val WATER_SALT = 0x53656153L

  /** No owner yet. Not a valid region index, and [assign] guarantees none survive. */
  const val UNCLAIMED = -1

  fun assign(
    grid: CellRegion,
    elevation: FloatLayer,
    biome: IntLayer,
    discharge: FloatLayer?,
    worldSeed: Long,
    bounds: Aabb,
    wrapX: Boolean,
    wrapY: Boolean,
    params: RegionParams
  ): Assignment {
    val width = grid.width
    val height = grid.height
    val metres = grid.resolution.metresPerCell

    // Two independent samples at two spacings, each keeping only the seeds that landed in its own
    // element. Sampling once and splitting by element would give the sea the land's grain, and sampling
    // the sea against the land's own point set would reject seeds for being too close to a point on the
    // far side of a coastline they can never grow across.
    val land = PoissonDisk.sample(bounds, params.spacing, GenRng(GenRng.hash(worldSeed, SEED_SALT)))
    val sea = PoissonDisk.sample(
      bounds,
      params.spacing * params.waterSpacingFactor,
      GenRng(GenRng.hash(worldSeed, SEED_SALT, WATER_SALT))
    )
    require(land.isNotEmpty()) { "no region seeds fitted in $bounds at ${params.spacing} m" }

    val owner = IntArray(width * height) { UNCLAIMED }
    val dist = DoubleArray(width * height) { Double.POSITIVE_INFINITY }
    val settled = BooleanArray(width * height)
    val heap = DoubleIntHeap(width * height / 4 + 1)

    val regionWater = ArrayList<Boolean>(land.size + sea.size)
    val regionSeeds = ArrayList<Vec2d>(land.size + sea.size)

    val water = waterMask(grid, biome)

    for ((seed, wantsWater) in land.map { it to false } + sea.map { it to true }) {
      val cell = localCellOf(seed, grid, metres) ?: continue
      if (water[cell] != wantsWater) continue

      // Poisson spacing is kilometres wider than a cell, so two seeds of one element sharing a cell is
      // not reachable - but the two element passes are independent, and a seed landing on a claimed cell
      // must not open a second region with no ground.
      if (owner[cell] != UNCLAIMED) continue

      val index = regionSeeds.size
      regionSeeds.add(seed)
      regionWater.add(water[cell])
      owner[cell] = index
      dist[cell] = 0.0
      heap.push(0.0, cell)
    }

    val elevations = elevationCache(grid, elevation)
    val biomes = biomeCache(grid, biome)
    val rivers = riverMask(grid, discharge, params)

    while (!heap.isEmpty) {
      val cell = heap.pop()
      if (settled[cell]) continue
      settled[cell] = true

      val cx = cell % width
      val cy = cell / width

      for (direction in CARDINALS) {
        val nx = fold(cx + D8.DX[direction], width, wrapX) ?: continue
        val ny = fold(cy + D8.DY[direction], height, wrapY) ?: continue

        val next = ny * width + nx
        if (settled[next] || water[next] != water[cell]) continue

        var step = 1.0
        if (biomes[next] != biomes[cell]) step += params.biomePenalty
        step += params.reliefPenalty * abs(elevations[next] - elevations[cell])
        if (rivers[next]) step += params.riverPenalty

        val alternative = dist[cell] + step
        if (alternative < dist[next]) {
          dist[next] = alternative
          owner[next] = owner[cell]
          heap.push(alternative, next)
        }
      }
    }

    coverUnclaimed(owner, water, regionSeeds, regionWater, grid, width, height, metres, wrapX, wrapY)

    return Assignment(
      owner = owner,
      regionCount = regionSeeds.size,
      seeds = regionSeeds,
      isWater = BooleanArray(regionWater.size) { regionWater[it] }
    )
  }

  /**
   * Gives every unreached cell a region of its own component.
   *
   * An island smaller than the seed spacing, or a lake with no seed in it, gets no seed of its own and
   * cannot be reached across the coastline wall. Rather than leave a hole - which would be an unnamed
   * corner of the world, the one thing this system promises does not exist - each such component
   * becomes a region seeded at its own first cell. A flood fill rather than a second Dijkstra: inside
   * one small component there is no boundary left for a cost field to place.
   */
  private fun coverUnclaimed(
    owner: IntArray,
    water: BooleanArray,
    regionSeeds: MutableList<Vec2d>,
    regionWater: MutableList<Boolean>,
    grid: CellRegion,
    width: Int,
    height: Int,
    metres: Double,
    wrapX: Boolean,
    wrapY: Boolean
  ) {
    val queue = ArrayDeque<Int>()

    for (cell in owner.indices) {
      if (owner[cell] != UNCLAIMED) continue

      val index = regionSeeds.size
      regionSeeds.add(
        Vec2d(
          (grid.minX + cell % width + 0.5) * metres,
          (grid.minY + cell / width + 0.5) * metres
        )
      )
      regionWater.add(water[cell])
      owner[cell] = index
      queue.addLast(cell)

      while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val cx = current % width
        val cy = current / width

        for (direction in CARDINALS) {
          val nx = fold(cx + D8.DX[direction], width, wrapX) ?: continue
          val ny = fold(cy + D8.DY[direction], height, wrapY) ?: continue

          val next = ny * width + nx
          if (owner[next] != UNCLAIMED || water[next] != water[current]) continue

          owner[next] = index
          queue.addLast(next)
        }
      }
    }
  }

  private fun waterMask(grid: CellRegion, biome: IntLayer): BooleanArray {
    val mask = BooleanArray(grid.width * grid.height)
    for (ly in 0 until grid.height) {
      for (lx in 0 until grid.width) {
        val classified = Biome.entries[biome[grid.minX + lx, grid.minY + ly]]
        mask[ly * grid.width + lx] = classified.isWater
      }
    }
    return mask
  }

  /** Flattened so the inner loop reads one array rather than going through a layer's clamping. */
  private fun elevationCache(grid: CellRegion, elevation: FloatLayer): DoubleArray {
    val cache = DoubleArray(grid.width * grid.height)
    for (ly in 0 until grid.height) {
      for (lx in 0 until grid.width) {
        cache[ly * grid.width + lx] = elevation[grid.minX + lx, grid.minY + ly].toDouble()
      }
    }
    return cache
  }

  private fun biomeCache(grid: CellRegion, biome: IntLayer): IntArray {
    val cache = IntArray(grid.width * grid.height)
    for (ly in 0 until grid.height) {
      for (lx in 0 until grid.width) {
        cache[ly * grid.width + lx] = biome[grid.minX + lx, grid.minY + ly]
      }
    }
    return cache
  }

  /**
   * Which cells carry a channel worth dividing two places.
   *
   * From the `DISCHARGE` raster rather than from `RIVER_CHANNEL` features, because the inner loop needs
   * an O(1) answer per cell and a feature query per cell would cost more than the whole partition. A
   * partial pipeline with no hydrology gets no river boundaries, which is the right answer there rather
   * than a failure - the same call `climate/WeatherRegions` makes for mana.
   */
  private fun riverMask(grid: CellRegion, discharge: FloatLayer?, params: RegionParams): BooleanArray {
    val mask = BooleanArray(grid.width * grid.height)
    if (discharge == null || params.riverPenalty == 0.0) return mask

    for (ly in 0 until grid.height) {
      for (lx in 0 until grid.width) {
        val flow = discharge[grid.minX + lx, grid.minY + ly]
        mask[ly * grid.width + lx] = flow >= params.riverDischarge
      }
    }
    return mask
  }

  private fun localCellOf(position: Vec2d, grid: CellRegion, metres: Double): Int? {
    val lx = Math.floor(position.x / metres).toInt() - grid.minX
    val ly = Math.floor(position.y / metres).toInt() - grid.minY
    if (lx < 0 || ly < 0 || lx >= grid.width || ly >= grid.height) return null
    return ly * grid.width + lx
  }

  /** A neighbour index folded onto a wrapping axis, or null where the world simply ends. */
  private fun fold(value: Int, extent: Int, wraps: Boolean): Int? {
    if (wraps) return Math.floorMod(value, extent)
    if (value < 0 || value >= extent) return null
    return value
  }

  /** East, north, west, south - the even entries of [D8]'s contractual order. */
  private val CARDINALS = intArrayOf(0, 2, 4, 6)
}
