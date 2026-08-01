package net.bestia.worldgen.geo

import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Tuning for [DropletHeightField]. */
data class DropletParams(

  /**
   * Whether droplet erosion runs at all. **Off.**
   *
   * The architecture document argues *against* this feature: "any error in that blend puts back exactly the
   * seams the vector tier exists to remove." That argument is sound, and the answer to it is not confidence but
   * a default. The machinery below is written so the blend cannot be wrong - see the class KDoc - and it is
   * still off, because a seam is the one class of defect this pipeline is built to make impossible and the cost
   * of being wrong is paid by every chunk in every world.
   *
   * Turn it on per call site, look at `probe`, and run `ChunkSeamCheck` at several world sizes before believing
   * anything. `ChunkSeamTest` runs the check with it on, which is the entire safety argument.
   */
  val enabled: Boolean = false,

  /**
   * Side of a droplet tile in metres: the lattice spacing, not the simulated area.
   *
   * Each tile simulates twice this across, centred on its lattice point, so neighbouring tiles overlap by a
   * full tile. Four times a 32 m chunk gives a 128 m lattice and a 256 m simulation - long enough for a gully
   * to develop and short enough that one tile is not most of a region.
   */
  val tileExtent: Double = 128.0,

  /** Grid spacing inside a tile, in metres. Two is under a gully's width and a quarter of the work of one. */
  val cellSize: Double = 2.0,

  /**
   * Droplets per square kilometre of simulated area.
   *
   * **Measured down from 60,000, which was far too many.** At that density the deltas came out with a median of
   * 3.25 m, a 90th percentile of 11.5 m and **7.3% of cells pinned at the 12 m clamp** - which means the clamp
   * was shaping the terrain rather than bounding a mistake. Droplet erosion is a feedback loop, so the useful
   * range is much narrower than it looks.
   *
   * 3,000 is where the measurements say "detail": a median of 0.12 m, a 90th percentile of 0.8 m, a 99th of
   * 4.9 m, and 0.1% of cells at the clamp. 6,000 doubles all of that and 12,000 doubles it again. The job of
   * this pass is to put gullies and debris fans on ground the analytic detail already shaped, so erring gentle
   * is erring towards what it is for - and a caller who wants a badlands can raise it.
   */
  val dropletsPerSquareKilometre: Double = 3_000.0,

  /** Steps a droplet may take before it is abandoned. */
  val maxSteps: Int = 64,

  /** How much of its previous direction a droplet keeps. Zero follows the steepest descent exactly. */
  val inertia: Double = 0.05,

  /** Sediment a droplet can carry per unit of speed, water and drop. */
  val capacity: Double = 4.0,

  /** Fraction of the surplus that is dropped, and of the deficit that is cut, per step. */
  val depositRate: Double = 0.3,
  val erodeRate: Double = 0.3,

  /** Radius over which a droplet's cut is spread, in cells. One cell of cut is a pinprick. */
  val erodeRadius: Int = 2,

  val gravity: Double = 4.0,
  val evaporation: Double = 0.02,

  /**
   * Metres a cell's height may be moved, either way.
   *
   * A hard clamp rather than a consequence of the tuning. Droplet erosion is a feedback loop - a cut deepens
   * the channel, which concentrates the next droplet into it - so a bad parameter set does not produce slightly
   * wrong terrain, it produces a crevasse. Bounding the output means the worst a mistuning can do is look
   * strange, and it gives the invariants something to assert.
   */
  val maxDelta: Double = 12.0,

  /** Tiles held before the cache is dropped. Each is a `DoubleArray` of `(2*tileExtent/cellSize + 1)^2`. */
  val cacheLimit: Int = 512
) : Params {
  init {
    require(tileExtent > 0.0) { "tileExtent must be positive" }
    require(cellSize > 0.0) { "cellSize must be positive" }
    // A tile grid is `(2 * tileExtent / cellSize + 1)^2` cells, so a cell coarser than the tile leaves a
    // single-cell grid with no slope anywhere in it and the pass silently does nothing.
    require(cellSize <= tileExtent) {
      "cellSize $cellSize is coarser than tileExtent $tileExtent, leaving a tile with no interior"
    }
    require(dropletsPerSquareKilometre >= 0.0) {
      "dropletsPerSquareKilometre must not be negative, was $dropletsPerSquareKilometre"
    }
    require(maxSteps >= 0) { "maxSteps must not be negative, was $maxSteps" }
    require(inertia in 0.0..1.0) { "inertia must be in [0,1], was $inertia" }
    require(capacity >= 0.0) { "capacity must not be negative, was $capacity" }
    require(depositRate in 0.0..1.0) { "depositRate must be in [0,1], was $depositRate" }
    require(erodeRate in 0.0..1.0) { "erodeRate must be in [0,1], was $erodeRate" }
    require(erodeRadius >= 0) { "erodeRadius must not be negative" }
    require(gravity >= 0.0) { "gravity must not be negative, was $gravity" }
    require(evaporation in 0.0..1.0) { "evaporation must be in [0,1], was $evaporation" }
    require(maxDelta >= 0.0) { "maxDelta must not be negative" }
    require(cacheLimit >= 1) { "cacheLimit must be at least 1, was $cacheLimit" }
  }

  override fun digest() = ParamsDigest()
    .put("enabled", enabled)
    .put("tileExtent", tileExtent)
    .put("cellSize", cellSize)
    .put("dropletsPerSquareKilometre", dropletsPerSquareKilometre)
    .put("maxSteps", maxSteps)
    .put("inertia", inertia)
    .put("capacity", capacity)
    .put("depositRate", depositRate)
    .put("erodeRate", erodeRate)
    .put("erodeRadius", erodeRadius)
    .put("gravity", gravity)
    .put("evaporation", evaporation)
    .put("maxDelta", maxDelta)
    .put("cacheLimit", cacheLimit)
}

/**
 * Particle droplet erosion over a base heightfield, as a decorator.
 *
 * This closes the architecture document's **deviation 1**, which the document argues against closing, and the
 * whole design is about the one sentence in that argument: *droplets are stateful and non-local; doing them
 * seam-free needs overlap-and-blend, and any error in that blend puts back exactly the seams the vector tier
 * exists to remove.*
 *
 * ### Why this cannot produce a seam
 *
 * `ChunkSeamCheck` compares columns two chunks share at `epsilon = 0.0` - **bit-identical**, not close. The
 * property that delivers that is narrow and worth stating exactly:
 *
 * > [heightAt] reads nothing but its own two arguments and immutable state.
 *
 * There is no chunk parameter to be tempted by. The tiles consulted for a position are decided by `floor(x/T)`
 * and `floor(y/T)`, they are visited in a fixed order, and their contributions are summed in that order. Two
 * chunks asking about the same world column therefore execute the same instructions on the same doubles and get
 * the same bits - not because the arithmetic is exact, but because it is *the same arithmetic*.
 *
 * That is why the tiles sit on a **fixed lattice** and are never keyed on the asking chunk. A tile per chunk,
 * simulated with a margin and blended in the overlap, is the design that fails: two chunks would each simulate
 * their own overlap, and two independent simulations of the same ground do not agree bit for bit however
 * carefully the weights are chosen.
 *
 * ### The blend
 *
 * Tile `(i,j)` is centred at `(i*T, j*T)` and simulates the square `±T` around it, so neighbouring tiles overlap
 * by a whole tile and every position is covered by exactly four. The weights are the separable tent
 * `(1-|u|)` - which is bilinear interpolation over the lattice, and therefore a partition of unity by
 * construction rather than by normalisation.
 *
 * The tent has a second job. It is **zero exactly at the edge of each tile's simulated square**, which is where
 * that tile's simulation is least trustworthy: a droplet that reaches the boundary has nowhere to flow and is
 * abandoned. So the region a tile is worst at is the region it contributes nothing to, and no separate window
 * or taper is needed.
 *
 * ### What is blended
 *
 * The **delta**, never the height. Where erosion does nothing the sum is exactly zero and [heightAt] returns
 * the inner field's own value bit for bit, so switching this on cannot perturb ground it did not erode. It also
 * means the inner field stays the single source of large-scale shape, and this can only add gullies to it.
 *
 * ### Where it runs
 *
 * On the base heightfield **before vector features are stamped**, which is what wrapping [BaseHeightField]
 * gets for free: `ChunkHeightSampler` stamps features over whatever this returns. Droplets that ran after the
 * channels were cut would erode the channel that hydrology had just carved.
 */
class DropletHeightField(
  private val inner: BaseHeightField,
  seed: Long,
  private val params: DropletParams = DropletParams()
) : BaseHeightField {

  private val dropletSeed = GenRng.mix64(seed xor DROPLET_SALT)

  /** Cells across one tile's simulated square, which spans two lattice steps. */
  private val gridSize = (2.0 * params.tileExtent / params.cellSize).toInt() + 1

  /**
   * Per-tile height deltas, computed once and shared.
   *
   * `ConcurrentHashMap.computeIfAbsent` rather than a lock around the whole cache: `ChunkSeamCheck` runs four
   * threads and any real chunk worker runs more, and a single lock would serialise the expensive part. Two
   * threads that want the *same* tile do serialise, which is the behaviour wanted - the second gets the first's
   * result rather than simulating it again.
   */
  private val tiles = ConcurrentHashMap<Long, DoubleArray>()

  override fun heightAt(worldX: Double, worldY: Double): Double {
    val base = inner.heightAt(worldX, worldY)
    if (!params.enabled) return base

    val tileX = worldX / params.tileExtent
    val tileY = worldY / params.tileExtent
    val i0 = floor(tileX).toInt()
    val j0 = floor(tileY).toInt()
    val fx = tileX - i0
    val fy = tileY - j0

    // Bilinear over the lattice: four tiles, weights summing to one, in a fixed order.
    var delta = 0.0
    for (dj in 0..1) {
      for (di in 0..1) {
        val weight = weightOf(di, dj, fx, fy)
        if (weight <= 0.0) continue
        delta += weight * deltaAt(i0 + di, j0 + dj, worldX, worldY)
      }
    }

    return base + delta
  }

  /**
   * Weight of the tile at lattice offset [di],[dj] for a position [fx],[fy] of the way through its cell.
   *
   * Extracted so `DropletErosionTest` can assert the partition of unity against *this* arithmetic rather than
   * against a copy of it. The property - that the four weights sum to one - is the whole of why the blend cannot
   * bias the terrain, and a test that reimplements the weights would pass even if these changed.
   */
  internal fun weightOf(di: Int, dj: Int, fx: Double, fy: Double): Double {
    val wx = if (di == 0) 1.0 - fx else fx
    val wy = if (dj == 0) 1.0 - fy else fy
    return wx * wy
  }

  /** Where a position sits within its lattice cell, as a fraction in `[0,1)` on each axis. */
  internal fun cellFractionAt(worldX: Double, worldY: Double): Pair<Double, Double> {
    val tileX = worldX / params.tileExtent
    val tileY = worldY / params.tileExtent
    return (tileX - floor(tileX)) to (tileY - floor(tileY))
  }

  /** This tile's delta at a world position, sampled bilinearly from its grid. */
  private fun deltaAt(i: Int, j: Int, worldX: Double, worldY: Double): Double {
    val grid = tileFor(i, j)

    // Grid cell 0 sits at the tile's lower-left corner, one lattice step below its centre.
    val originX = (i - 1) * params.tileExtent
    val originY = (j - 1) * params.tileExtent
    val gx = (worldX - originX) / params.cellSize
    val gy = (worldY - originY) / params.cellSize

    val x0 = floor(gx).toInt()
    val y0 = floor(gy).toInt()
    val tx = gx - x0
    val ty = gy - y0

    val v00 = at(grid, x0, y0)
    val v10 = at(grid, x0 + 1, y0)
    val v01 = at(grid, x0, y0 + 1)
    val v11 = at(grid, x0 + 1, y0 + 1)

    val bottom = v00 + (v10 - v00) * tx
    val top = v01 + (v11 - v01) * tx
    return bottom + (top - bottom) * ty
  }

  private fun at(grid: DoubleArray, x: Int, y: Int): Double {
    if (x < 0 || y < 0 || x >= gridSize || y >= gridSize) return 0.0
    return grid[y * gridSize + x]
  }

  private fun tileFor(i: Int, j: Int): DoubleArray {
    // Crude but safe: a long session over a large world would otherwise hold every tile it ever visited.
    // Dropping the lot costs recomputation and nothing else, because a tile is a pure function of its index.
    if (tiles.size > params.cacheLimit) tiles.clear()

    val key = (i.toLong() shl 32) xor (j.toLong() and 0xFFFFFFFFL)
    return tiles.computeIfAbsent(key) { simulate(i, j) }
  }

  /**
   * Runs the droplets for one tile and returns its height deltas.
   *
   * A pure function of the tile index and the world seed, which is what makes the cache a cache rather than
   * state: evicting it changes performance and nothing else.
   */
  private fun simulate(i: Int, j: Int): DoubleArray {
    val originX = (i - 1) * params.tileExtent
    val originY = (j - 1) * params.tileExtent

    // The surface the droplets run over, sampled from the inner field. Held separately from the delta so a
    // droplet reads the *original* ground: letting droplets erode each other's channels within one tile turns
    // the feedback loop up by a factor of the droplet count and produces canyons.
    val height = DoubleArray(gridSize * gridSize)
    for (y in 0 until gridSize) {
      for (x in 0 until gridSize) {
        height[y * gridSize + x] = inner.heightAt(
          originX + x * params.cellSize,
          originY + y * params.cellSize
        )
      }
    }

    val delta = DoubleArray(gridSize * gridSize)
    val simulatedMetres = 2.0 * params.tileExtent
    val squareKilometres = simulatedMetres * simulatedMetres / 1_000_000.0
    val droplets = (params.dropletsPerSquareKilometre * squareKilometres).toInt()

    for (n in 0 until droplets) {
      // Deterministic per tile and per droplet. Not a shared RNG: a shared stream would make the result depend
      // on how many droplets ran before it, and therefore on nothing that heightAt can reproduce.
      val startX = GenRng.hashUnit(dropletSeed, i.toLong(), j.toLong(), n.toLong()) * (gridSize - 1)
      val startY = GenRng.hashUnit(dropletSeed, i.toLong(), j.toLong(), n.toLong(), 1L) * (gridSize - 1)
      runDroplet(height, delta, startX, startY)
    }

    for (k in delta.indices) {
      delta[k] = delta[k].coerceIn(-params.maxDelta, params.maxDelta)
    }

    return delta
  }

  /**
   * One droplet, from [startX],[startY] in grid coordinates.
   *
   * The standard particle model: carry water and sediment downhill, cut where the flow has capacity to spare
   * and drop where it does not. What it buys over analytic noise is the thing the architecture document names -
   * sediment transport, so a gully has a debris fan at the bottom of it instead of just ending.
   */
  private fun runDroplet(height: DoubleArray, delta: DoubleArray, startX: Double, startY: Double) {
    var x = startX
    var y = startY
    var dirX = 0.0
    var dirY = 0.0
    var speed = 1.0
    var water = 1.0
    var sediment = 0.0

    for (step in 0 until params.maxSteps) {
      val cellX = x.toInt()
      val cellY = y.toInt()
      if (cellX < 0 || cellY < 0 || cellX >= gridSize - 1 || cellY >= gridSize - 1) return

      val (gradX, gradY) = gradientAt(height, x, y)

      dirX = dirX * params.inertia - gradX * (1.0 - params.inertia)
      dirY = dirY * params.inertia - gradY * (1.0 - params.inertia)

      val length = sqrt(dirX * dirX + dirY * dirY)
      // A droplet in a pit has nowhere to go, so it puts down what it is carrying and stops - which is what
      // filling a pit *is*.
      if (length < 1e-9) {
        deposit(delta, x, y, sediment)
        return
      }
      dirX /= length
      dirY /= length

      val fromHeight = sample(height, x, y)
      x += dirX
      y += dirY
      // Off the simulated square. Its load goes with it, correctly: the tent weight is zero here, so this
      // region contributes nothing either way, and the neighbouring tile simulates the same ground properly.
      if (x < 0.0 || y < 0.0 || x > gridSize - 1.0 || y > gridSize - 1.0) return

      val drop = sample(height, x, y) - fromHeight

      val room = max(-drop * speed * water * params.capacity, MIN_CAPACITY)

      if (drop > 0.0 || sediment > room) {
        // Uphill, or more sediment than the flow can hold: drop some. Uphill deposits at most the step, so a
        // droplet cannot build a hill higher than the one it just failed to climb.
        val amount = if (drop > 0.0) min(drop, sediment) else (sediment - room) * params.depositRate
        sediment -= amount
        deposit(delta, x - dirX, y - dirY, amount)
      } else {
        // Room to spare: cut, but never more than the drop itself, or the droplet digs its own waterfall.
        val amount = min((room - sediment) * params.erodeRate, -drop)
        sediment += amount
        erode(delta, x - dirX, y - dirY, amount)
      }

      speed = sqrt(max(0.0, speed * speed - drop * params.gravity))
      water *= (1.0 - params.evaporation)
      if (water < MIN_WATER) {
        deposit(delta, x, y, sediment)
        return
      }
    }

    // Out of steps while still loaded. **Dropping the load here is what makes this sediment *transport*
    // rather than sediment removal**, and leaving it out was measurable: 94% of moved cells were cut and 5%
    // filled, a 19:1 ratio, because every droplet that evaporated or ran out of steps deleted what it carried.
    // The whole reason to prefer droplets over analytic noise is the debris fan at the bottom of the gully.
    deposit(delta, x, y, sediment)
  }

  /** Gradient of the surface at a grid position, by forward differences on the bilinear surface. */
  private fun gradientAt(height: DoubleArray, x: Double, y: Double): Pair<Double, Double> {
    val here = sample(height, x, y)
    val east = sample(height, min(x + 1.0, gridSize - 1.0), y)
    val north = sample(height, x, min(y + 1.0, gridSize - 1.0))
    return (east - here) to (north - here)
  }

  private fun sample(height: DoubleArray, x: Double, y: Double): Double {
    val x0 = x.toInt().coerceIn(0, gridSize - 2)
    val y0 = y.toInt().coerceIn(0, gridSize - 2)
    val tx = (x - x0).coerceIn(0.0, 1.0)
    val ty = (y - y0).coerceIn(0.0, 1.0)

    val v00 = height[y0 * gridSize + x0]
    val v10 = height[y0 * gridSize + x0 + 1]
    val v01 = height[(y0 + 1) * gridSize + x0]
    val v11 = height[(y0 + 1) * gridSize + x0 + 1]

    val bottom = v00 + (v10 - v00) * tx
    val top = v01 + (v11 - v01) * tx
    return bottom + (top - bottom) * ty
  }

  /** Deposition goes to the four cells around the position, bilinearly - a point deposit is a spike. */
  private fun deposit(delta: DoubleArray, x: Double, y: Double, amount: Double) {
    if (amount == 0.0) return
    val x0 = x.toInt().coerceIn(0, gridSize - 2)
    val y0 = y.toInt().coerceIn(0, gridSize - 2)
    val tx = (x - x0).coerceIn(0.0, 1.0)
    val ty = (y - y0).coerceIn(0.0, 1.0)

    delta[y0 * gridSize + x0] += amount * (1 - tx) * (1 - ty)
    delta[y0 * gridSize + x0 + 1] += amount * tx * (1 - ty)
    delta[(y0 + 1) * gridSize + x0] += amount * (1 - tx) * ty
    delta[(y0 + 1) * gridSize + x0 + 1] += amount * tx * ty
  }

  /**
   * A cut, spread over a disc of [DropletParams.erodeRadius] cells.
   *
   * Spread rather than applied at a point because a one-cell cut at two-metre spacing is a pinprick that the
   * bilinear sample smooths away to nothing: the erosion would be computed, stored and invisible.
   */
  private fun erode(delta: DoubleArray, x: Double, y: Double, amount: Double) {
    if (amount == 0.0) return
    val radius = params.erodeRadius
    if (radius == 0) {
      deposit(delta, x, y, -amount)
      return
    }

    val centreX = x.toInt()
    val centreY = y.toInt()

    // Two passes so the weights are normalised: a cut near the grid edge would otherwise remove less than it
    // said it did, which is a systematic bias towards the tile's interior.
    var total = 0.0
    for (dy in -radius..radius) {
      for (dx in -radius..radius) {
        val cx = centreX + dx
        val cy = centreY + dy
        if (cx < 0 || cy < 0 || cx >= gridSize || cy >= gridSize) continue
        total += max(0.0, 1.0 - sqrt((dx * dx + dy * dy).toDouble()) / (radius + 1.0))
      }
    }
    if (total <= 0.0) return

    for (dy in -radius..radius) {
      for (dx in -radius..radius) {
        val cx = centreX + dx
        val cy = centreY + dy
        if (cx < 0 || cy < 0 || cx >= gridSize || cy >= gridSize) continue
        val weight = max(0.0, 1.0 - sqrt((dx * dx + dy * dy).toDouble()) / (radius + 1.0))
        if (weight > 0.0) delta[cy * gridSize + cx] -= amount * weight / total
      }
    }
  }

  /** Largest absolute delta over the tiles simulated so far. For tests and the probe, not for generation. */
  fun worstDelta(): Double = tiles.values.maxOfOrNull { grid -> grid.maxOf { abs(it) } } ?: 0.0

  private companion object {
    const val DROPLET_SALT = 0x3D50915E7C24B1L

    /** Floor on carrying capacity, so a droplet on flat ground still moves a little sediment. */
    const val MIN_CAPACITY = 0.01

    /** Water below which a droplet has evaporated and is done. */
    const val MIN_WATER = 0.01
  }
}
