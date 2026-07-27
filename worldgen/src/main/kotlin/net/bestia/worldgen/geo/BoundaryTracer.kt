package net.bestia.worldgen.geo

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.fields.IntGrid
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Vec2d

/**
 * Turns a plate-id raster into plate-boundary polylines.
 *
 * The raster knows which plate each cell belongs to; it does not know that a particular boundary is
 * one continuous 900 km arc. Downstream stages need the arc: ore genesis follows a boundary along its
 * length, volcanic vents are spaced along it, and a fault trace that a chunk can carve has to be a
 * curve rather than a set of cells. Extracting it once here is also what stops five later stages from
 * each re-deriving boundaries with their own tie-breaking and quietly disagreeing about where they are.
 */
object BoundaryTracer {

  /** One traced boundary: the two plates it separates and the curve between them. */
  data class Trace(val plateA: Int, val plateB: Int, val line: Polyline)

  /**
   * @param minVertices runs shorter than this are dropped. Short runs are the ragged corners where
   *   three plates meet; they carry no information and would triple the feature count.
   */
  fun trace(
    plates: IntGrid,
    region: CellRegion,
    minVertices: Int = 6,
    smoothing: Int = 2
  ): List<Trace> {
    val metres = region.resolution.metresPerCell
    val byPair = LinkedHashMap<Long, ArrayList<Vec2d>>()

    // Boundary points sit on cell *edges*, not cell centres, so the trace runs between the two plates
    // rather than inside one of them.
    for (y in 0 until plates.height) {
      for (x in 0 until plates.width) {
        val here = plates.data[plates.index(x, y)]

        if (x + 1 < plates.width) {
          val east = plates.data[plates.index(x + 1, y)]
          if (east != here) {
            byPair.getOrPut(pairKey(here, east)) { ArrayList() }
              .add(worldPoint(region, metres, x + 1.0, y + 0.5))
          }
        }
        if (y + 1 < plates.height) {
          val north = plates.data[plates.index(x, y + 1)]
          if (north != here) {
            byPair.getOrPut(pairKey(here, north)) { ArrayList() }
              .add(worldPoint(region, metres, x + 0.5, y + 1.0))
          }
        }
      }
    }

    val traces = ArrayList<Trace>()
    // Sorted, because feature ids are assigned in emission order and must not depend on map order.
    for (key in byPair.keys.sorted()) {
      val points = byPair.getValue(key)
      if (points.size < minVertices) continue

      val plateA = (key ushr 32).toInt()
      val plateB = (key and 0xFFFFFFFFL).toInt()

      for (run in chain(points, maxGap = metres * MAX_GAP_CELLS)) {
        if (run.size < minVertices) continue

        // Resample before smoothing: the raw chain zigzags at half-cell scale between the east-edge
        // and north-edge points of the same cell, and corner cutting a zigzag just shortens it.
        val raw = Polyline(run)
        val line = raw.resample(metres).let { if (smoothing > 0) it.chaikin(smoothing) else it }
        traces.add(Trace(plateA, plateB, line))
      }
    }

    return traces
  }

  /**
   * Greedy nearest-neighbour chaining, started from an endpoint where there is one.
   *
   * Good enough because the input is a set of points on a lattice that already forms curves: the only
   * ambiguity is at junctions, and there the greedy walk simply ends a run and starts another, which
   * is the right answer anyway - a triple junction is genuinely three boundaries, not one.
   */
  private fun chain(points: List<Vec2d>, maxGap: Double): List<List<Vec2d>> {
    val n = points.size
    val visited = BooleanArray(n)
    val gapSq = maxGap * maxGap

    // Bucket the points so the walk does not rescan the whole set at every step. Boundary point sets
    // run to tens of thousands of points on a large world; quadratic chaining is not an option.
    val buckets = HashMap<Long, ArrayList<Int>>()
    fun bucketOf(p: Vec2d) =
      (Math.floor(p.x / maxGap).toLong() shl 32) or (Math.floor(p.y / maxGap).toLong() and 0xFFFFFFFFL)

    for (i in 0 until n) {
      buckets.getOrPut(bucketOf(points[i])) { ArrayList() }.add(i)
    }

    fun neighboursOf(p: Vec2d, action: (Int) -> Unit) {
      val bx = Math.floor(p.x / maxGap).toLong()
      val by = Math.floor(p.y / maxGap).toLong()
      for (dy in -1..1) {
        for (dx in -1..1) {
          val key = ((bx + dx) shl 32) or ((by + dy) and 0xFFFFFFFFL)
          buckets[key]?.forEach(action)
        }
      }
    }

    // Precomputed rather than evaluated inside the comparator, which would call it O(n log n) times.
    val degree = IntArray(n)
    for (i in 0 until n) {
      neighboursOf(points[i]) { j ->
        if (j != i && points[i].distanceSquaredTo(points[j]) <= gapSq) degree[i]++
      }
    }

    // Endpoints first, so a run is traced from one end rather than from its middle - which would
    // otherwise split every open curve into two.
    val order = (0 until n).sortedWith(compareBy({ degree[it] }, { it }))

    val runs = ArrayList<List<Vec2d>>()
    for (start in order) {
      if (visited[start]) continue

      val run = ArrayList<Vec2d>()
      var current = start
      visited[current] = true
      run.add(points[current])

      while (true) {
        var best = -1
        var bestSq = Double.POSITIVE_INFINITY
        neighboursOf(points[current]) { j ->
          if (!visited[j]) {
            val d = points[current].distanceSquaredTo(points[j])
            if (d <= gapSq && (d < bestSq || (d == bestSq && j < best))) {
              bestSq = d
              best = j
            }
          }
        }
        if (best < 0) break

        visited[best] = true
        run.add(points[best])
        current = best
      }

      if (run.size >= 2) runs.add(run)
    }

    return runs
  }

  private fun worldPoint(region: CellRegion, metres: Double, cellX: Double, cellY: Double) =
    Vec2d((region.minX + cellX) * metres, (region.minY + cellY) * metres)

  private fun pairKey(a: Int, b: Int): Long {
    val lo = minOf(a, b)
    val hi = maxOf(a, b)
    return (lo.toLong() shl 32) or (hi.toLong() and 0xFFFFFFFFL)
  }

  /** How far apart two boundary points may be and still belong to the same run, in cells. */
  private const val MAX_GAP_CELLS = 1.5
}
