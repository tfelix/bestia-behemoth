package net.bestia.worldgen.fields

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Blue-noise point sets: no two points closer than a minimum distance, and no clumping.
 *
 * Used for plate seeds, hotspots, ore deposits and settlement candidates. Uniform random points are
 * the wrong tool for all of them - Poisson-disk sampling of ~120 plate seeds looks like a tectonic
 * map, while 120 uniform points produce a handful of slivers and a couple of continent-sized plates,
 * and the resulting world reads as arbitrary rather than as geological.
 */
object PoissonDisk {

  /**
   * Bridson's algorithm: grow outwards from a seed point, keeping an active front, and place each new
   * point in the annulus `[r, 2r]` around a point taken from that front.
   *
   * Deterministic given [rng]: the front is consumed in an order the stream itself chooses, so the
   * result depends only on the seed the stream was derived from, never on iteration order elsewhere.
   *
   * @param candidatesPerPoint attempts before a front point is retired. 30 is Bridson's figure; lower
   *   is faster and leaves slightly larger gaps.
   */
  fun sample(
    bounds: Aabb,
    minDistance: Double,
    rng: GenRng,
    candidatesPerPoint: Int = 30
  ): List<Vec2d> {
    require(minDistance > 0.0) { "minDistance must be positive, was $minDistance" }

    // One point per background cell at this size, so a neighbour check only has to look at the 5x5
    // block around a candidate.
    val cellSize = minDistance / sqrt(2.0)
    val cols = max(1, ceil(bounds.width / cellSize).toInt())
    val rows = max(1, ceil(bounds.height / cellSize).toInt())
    val occupant = IntArray(cols * rows) { -1 }

    val points = ArrayList<Vec2d>()
    val front = ArrayList<Int>()

    fun cellOf(p: Vec2d): Int {
      val cx = ((p.x - bounds.minX) / cellSize).toInt().coerceIn(0, cols - 1)
      val cy = ((p.y - bounds.minY) / cellSize).toInt().coerceIn(0, rows - 1)
      return cy * cols + cx
    }

    fun isFarEnough(p: Vec2d): Boolean {
      val cx = ((p.x - bounds.minX) / cellSize).toInt()
      val cy = ((p.y - bounds.minY) / cellSize).toInt()
      val minSq = minDistance * minDistance

      for (dy in -2..2) {
        for (dx in -2..2) {
          val nx = cx + dx
          val ny = cy + dy
          if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue
          val other = occupant[ny * cols + nx]
          if (other >= 0 && points[other].distanceSquaredTo(p) < minSq) return false
        }
      }
      return true
    }

    fun accept(p: Vec2d) {
      occupant[cellOf(p)] = points.size
      front.add(points.size)
      points.add(p)
    }

    accept(
      Vec2d(
        rng.nextDouble(bounds.minX, bounds.maxX),
        rng.nextDouble(bounds.minY, bounds.maxY)
      )
    )

    while (front.isNotEmpty()) {
      // Removing a random front entry rather than the last is what keeps the growth isotropic;
      // always taking the last produces a visible spiral.
      val pick = rng.nextInt(front.size)
      val origin = points[front[pick]]
      var placed = false

      for (attempt in 0 until candidatesPerPoint) {
        val angle = rng.nextDouble() * TAU
        val radius = minDistance * (1.0 + rng.nextDouble())
        val candidate = Vec2d(origin.x + cos(angle) * radius, origin.y + sin(angle) * radius)

        if (!bounds.contains(candidate.x, candidate.y)) continue
        if (!isFarEnough(candidate)) continue

        accept(candidate)
        placed = true
        break
      }

      if (!placed) {
        front[pick] = front[front.size - 1]
        front.removeAt(front.size - 1)
      }
    }

    return points
  }

  private const val TAU = 2.0 * Math.PI
}

/**
 * A uniform bucket grid over a point set, for nearest-neighbour queries.
 *
 * The tectonics stage needs the two nearest plate seeds for every cell in the world. At 4096x4096
 * cells and 200 seeds, brute force is 3.4 billion distance computations; bucketed it is a few tens of
 * millions. That difference is between a world that takes seconds to birth and one that takes minutes.
 */
class PointIndex(private val points: List<Vec2d>, bounds: Aabb, targetPerBucket: Double = 2.0) {

  private val minX = bounds.minX
  private val minY = bounds.minY
  private val cellSize: Double
  private val cols: Int
  private val rows: Int

  /** Point indices grouped by bucket, laid out as one flat array with a start offset per bucket. */
  private val bucketStart: IntArray
  private val bucketItems: IntArray

  init {
    require(points.isNotEmpty()) { "A point index needs at least one point" }

    val area = max(1.0, bounds.width * bounds.height)
    cellSize = max(1e-9, sqrt(area * targetPerBucket / points.size))
    cols = max(1, ceil(bounds.width / cellSize).toInt())
    rows = max(1, ceil(bounds.height / cellSize).toInt())

    val counts = IntArray(cols * rows + 1)
    val bucketOf = IntArray(points.size)
    for (i in points.indices) {
      val b = bucketIndex(points[i].x, points[i].y)
      bucketOf[i] = b
      counts[b + 1]++
    }
    for (b in 1 until counts.size) counts[b] += counts[b - 1]

    bucketStart = counts
    bucketItems = IntArray(points.size)
    val cursor = counts.copyOf()
    for (i in points.indices) {
      bucketItems[cursor[bucketOf[i]]++] = i
    }
  }

  /**
   * The two nearest points to `(x, y)`, nearest first.
   *
   * Both are returned together because the caller almost always wants both: the *nearest* seed is
   * which plate a cell belongs to, and the gap to the *second* nearest is how far the cell is from
   * that plate's boundary. Computing them in one traversal halves the work of the tectonics stage.
   *
   * @param out receives `[nearestIndex, secondIndex, nearestDistance, secondDistance]`; the second
   *   entry is -1 with an infinite distance when there is only one point.
   */
  fun nearestTwo(x: Double, y: Double, out: DoubleArray) {
    require(out.size >= 4) { "out needs 4 slots, had ${out.size}" }

    var best = -1
    var second = -1
    var bestSq = Double.POSITIVE_INFINITY
    var secondSq = Double.POSITIVE_INFINITY

    val cx = ((x - minX) / cellSize).toInt().coerceIn(0, cols - 1)
    val cy = ((y - minY) / cellSize).toInt().coerceIn(0, rows - 1)

    var ring = 0
    val maxRing = max(cols, rows)
    while (ring <= maxRing) {
      // Stop once the closest anything in this ring could possibly be is further than what we have.
      // The -1 accounts for the query point sitting anywhere inside its own bucket.
      if (second >= 0 && ring > 1) {
        val minPossible = (ring - 1) * cellSize
        if (minPossible * minPossible > secondSq) break
      }

      for (dy in -ring..ring) {
        for (dx in -ring..ring) {
          // Only the outer shell of the block is new on this iteration.
          if (ring > 0 && kotlin.math.abs(dx) != ring && kotlin.math.abs(dy) != ring) continue

          val bx = cx + dx
          val by = cy + dy
          if (bx < 0 || by < 0 || bx >= cols || by >= rows) continue

          val bucket = by * cols + bx
          for (slot in bucketStart[bucket] until bucketStart[bucket + 1]) {
            val i = bucketItems[slot]
            val p = points[i]
            val ddx = p.x - x
            val ddy = p.y - y
            val distSq = ddx * ddx + ddy * ddy

            // Strict comparisons plus the index tie-break keep the result independent of the order
            // buckets happen to be visited in, which two equidistant seeds would otherwise expose.
            if (distSq < bestSq || (distSq == bestSq && i < best)) {
              second = best
              secondSq = bestSq
              best = i
              bestSq = distSq
            } else if (distSq < secondSq || (distSq == secondSq && i < second)) {
              second = i
              secondSq = distSq
            }
          }
        }
      }
      ring++
    }

    out[0] = best.toDouble()
    out[1] = second.toDouble()
    out[2] = sqrt(bestSq)
    out[3] = if (second >= 0) sqrt(secondSq) else Double.POSITIVE_INFINITY
  }

  fun nearest(x: Double, y: Double): Int {
    val scratch = DoubleArray(4)
    nearestTwo(x, y, scratch)
    return scratch[0].toInt()
  }

  private fun bucketIndex(x: Double, y: Double): Int {
    val cx = ((x - minX) / cellSize).toInt().coerceIn(0, cols - 1)
    val cy = ((y - minY) / cellSize).toInt().coerceIn(0, rows - 1)
    return cy * cols + cx
  }
}

/** Clamped linear interpolation into a table sampled at a regular interval. */
object Tables {

  /**
   * @param position index space, so 1.5 is halfway between `table[1]` and `table[2]`
   *
   * Interpolated rather than truncated, deliberately. Reading a regularly-sampled table with
   * `toInt()` turns it into a staircase whose tread is the sample interval, and any spline drawn
   * through those values then rings at that pitch - which shows up in the world as regular scalloping
   * at a spacing that matches nothing in the design.
   */
  fun linear(table: DoubleArray, position: Double): Double {
    require(table.isNotEmpty()) { "Cannot interpolate an empty table" }
    if (table.size == 1) return table[0]

    val at = position.coerceIn(0.0, (table.size - 1).toDouble())
    val i = floor(at).toInt().coerceAtMost(table.size - 2)
    return table[i] + (table[i + 1] - table[i]) * (at - i)
  }

  /** [linear] against a table whose samples are [spacing] apart in some real unit. */
  fun atSpacing(table: DoubleArray, position: Double, spacing: Double): Double {
    require(spacing > 0.0) { "spacing must be positive, was $spacing" }
    return linear(table, position / spacing)
  }
}
