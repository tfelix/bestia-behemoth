package net.bestia.worldgen.fields

import kotlin.math.sqrt

/**
 * Exact Euclidean distance transform of a boolean mask.
 *
 * Distance-to-ocean, distance-to-boundary and distance-to-river all fall out of this, and every one
 * of them is used to drive something a player will notice: continentality in the climate model,
 * foothill falloff away from a plate boundary, riparian biome strips.
 *
 * Felzenszwalb & Huttenlocher's separable algorithm - two O(n) passes over rows then columns, using
 * the lower envelope of a set of parabolas. Chosen over the usual two-pass chamfer approximation
 * because chamfer error is *directional*: it under-reports diagonal distances by a few percent, and
 * a climate field driven by it acquires faint diagonal banding that looks like a bug in the noise.
 */
object DistanceTransform {

  /**
   * Distance in cells from every cell to the nearest cell where [mask] is true.
   *
   * Cells in the mask get zero. If the mask is empty every cell gets [Double.MAX_VALUE], which
   * callers must handle - a world with no ocean at all is a legitimate seed, not a crash.
   */
  fun euclidean(width: Int, height: Int, mask: (x: Int, y: Int) -> Boolean): Grid {
    val squared = squaredEuclidean(width, height, mask)
    for (i in squared.data.indices) {
      val v = squared.data[i]
      squared.data[i] = if (v >= INFINITE) Double.MAX_VALUE else sqrt(v)
    }
    return squared
  }

  /** Distance in metres, for callers that think in world units rather than cells. */
  fun euclideanMetres(
    width: Int,
    height: Int,
    metresPerCell: Double,
    mask: (x: Int, y: Int) -> Boolean
  ): Grid {
    val grid = euclidean(width, height, mask)
    for (i in grid.data.indices) {
      if (grid.data[i] != Double.MAX_VALUE) grid.data[i] *= metresPerCell
    }
    return grid
  }

  /** Squared distance, for comparisons that do not need the square root. */
  fun squaredEuclidean(width: Int, height: Int, mask: (x: Int, y: Int) -> Boolean): Grid {
    val grid = Grid(width, height) { x, y -> if (mask(x, y)) 0.0 else INFINITE }

    val column = DoubleArray(height)
    val row = DoubleArray(width)
    val workspace = Workspace(maxOf(width, height))

    for (x in 0 until width) {
      for (y in 0 until height) column[y] = grid.data[y * width + x]
      transform1d(column, height, workspace)
      for (y in 0 until height) grid.data[y * width + x] = workspace.result[y]
    }

    for (y in 0 until height) {
      System.arraycopy(grid.data, y * width, row, 0, width)
      transform1d(row, width, workspace)
      System.arraycopy(workspace.result, 0, grid.data, y * width, width)
    }

    return grid
  }

  /** Reused across every row and column so a transform of a large grid allocates four arrays total. */
  private class Workspace(capacity: Int) {
    val result = DoubleArray(capacity)

    /** Index of the parabola that is lowest in each region of the envelope. */
    val vertex = IntArray(capacity)

    /** Boundaries between those regions; one more than there are parabolas. */
    val boundary = DoubleArray(capacity + 1)
  }

  /**
   * Lower envelope of the parabolas `(q - i)^2 + f[i]`.
   *
   * The intersection of two of them is at `s = ((f[q] + q^2) - (f[p] + p^2)) / (2q - 2p)`; a new
   * parabola that intersects the current one left of where the previous boundary sits has hidden it
   * completely, so that one is popped.
   */
  private fun transform1d(f: DoubleArray, n: Int, workspace: Workspace) {
    val v = workspace.vertex
    val z = workspace.boundary
    val d = workspace.result

    var k = 0
    v[0] = 0
    z[0] = Double.NEGATIVE_INFINITY
    z[1] = Double.POSITIVE_INFINITY

    for (q in 1 until n) {
      var s = intersection(f, q, v[k])
      while (s <= z[k]) {
        k--
        s = intersection(f, q, v[k])
      }
      k++
      v[k] = q
      z[k] = s
      z[k + 1] = Double.POSITIVE_INFINITY
    }

    k = 0
    for (q in 0 until n) {
      while (z[k + 1] < q) k++
      val dq = (q - v[k]).toDouble()
      d[q] = dq * dq + f[v[k]]
    }
  }

  private fun intersection(f: DoubleArray, q: Int, p: Int): Double =
    ((f[q] + q.toDouble() * q) - (f[p] + p.toDouble() * p)) / (2.0 * q - 2.0 * p)

  /**
   * Large but finite. `Double.POSITIVE_INFINITY` would make the parabola intersection `inf - inf`,
   * i.e. NaN, and the envelope would silently collapse.
   */
  private const val INFINITE = 1e18
}
