package net.bestia.worldgen.fields

/**
 * Marching squares over a boolean region, with the vertices placed by interpolating a field.
 *
 * Two inputs rather than one, and the split is the whole point. **Which side of the line a cell is on** comes
 * from the boolean, because "sea" is a connectivity question - water joined to the edge of the world - and no
 * threshold on elevation can answer it; an inland hollow below sea level is a salt lake and not a coast.
 * **Where the line runs inside a cell** comes from the field, so the result is sub-cell accurate rather than
 * stepping along the grid it was traced on.
 *
 * Segments are chained by **edge identity**, not by nearest neighbour: each segment names the two grid edges it
 * crosses, so following one to the next is exact. `geo/BoundaryTracer` chains greedily with a gap tolerance
 * because its input is a set of points and genuinely is ambiguous; a segment set is not, and inheriting the
 * heuristic would only add a way to be wrong.
 *
 * Everything is in **grid coordinates** - a vertex at `(3.5, 7.0)` lies half way along the edge between grid
 * points `(3, 7)` and `(4, 7)`. Mapping to world space is the caller's, which is also what keeps this in
 * `fields/` rather than dragging the vector tier down a layer.
 */
object ContourTrace {

  /** One traced boundary. [closed] is false only where the line ran off the edge of the grid. */
  class Contour(val xs: DoubleArray, val ys: DoubleArray, val closed: Boolean) {
    val size get() = xs.size
  }

  /**
   * Traces the boundary of [inside] over a [width] x [height] grid of points.
   *
   * @param field values at the same grid points, interpolated to place each vertex. Where two corners disagree
   *   about [inside] but not about the sign of [field] - which happens along an inland hollow that was excluded
   *   by connectivity - the vertex falls back to the middle of the edge rather than to an extrapolated one.
   * @param valid which grid points carry a real [field] value. A square with any invalid corner emits nothing:
   *   a caller that evaluates its field over a band and fills the rest with a sentinel would otherwise get a
   *   line traced along the edge of its own band, placed by interpolating a number that means nothing. Missing
   *   a piece of boundary is recoverable and inventing one is not, so the skip is the safe direction. Null
   *   means every point is valid.
   */
  fun trace(
    width: Int,
    height: Int,
    inside: BooleanArray,
    field: DoubleArray,
    valid: BooleanArray? = null
  ): List<Contour> {
    require(width >= 2 && height >= 2) { "a grid smaller than 2x2 has no square to march, was ${width}x$height" }
    require(inside.size == width * height) { "inside must cover the grid, was ${inside.size}" }
    require(field.size == width * height) { "field must cover the grid, was ${field.size}" }
    require(valid == null || valid.size == width * height) { "valid must cover the grid, was ${valid?.size}" }

    val horizontalEdges = width * height
    val vertexOf = HashMap<Int, Int>()
    val vx = ArrayList<Double>()
    val vy = ArrayList<Double>()

    // Each edge is crossed by at most one piece of line, so a vertex is created once and shared by both squares
    // either side of it. That sharing is what makes the chaining below exact rather than approximate.
    fun vertexAt(edge: Int): Int = vertexOf.getOrPut(edge) {
      val horizontal = edge < horizontalEdges
      val cell = if (horizontal) edge else edge - horizontalEdges
      val x = cell % width
      val y = cell / width

      val other = if (horizontal) y * width + (x + 1) else (y + 1) * width + x
      val a = field[y * width + x]
      val b = field[other]

      val span = a - b
      val t = if (span == 0.0) 0.5 else (a / span).let { if (it.isFinite() && it in 0.0..1.0) it else 0.5 }

      vx.add(if (horizontal) x + t else x.toDouble())
      vy.add(if (horizontal) y.toDouble() else y + t)
      vx.size - 1
    }

    // Segments, as pairs of vertex indices, plus the adjacency that chains them.
    val segA = ArrayList<Int>()
    val segB = ArrayList<Int>()
    val incident = HashMap<Int, MutableList<Int>>()

    fun emit(edgeA: Int, edgeB: Int) {
      val a = vertexAt(edgeA)
      val b = vertexAt(edgeB)
      if (a == b) return

      val segment = segA.size
      segA.add(a)
      segB.add(b)
      incident.getOrPut(a) { ArrayList(2) }.add(segment)
      incident.getOrPut(b) { ArrayList(2) }.add(segment)
    }

    for (y in 0 until height - 1) {
      for (x in 0 until width - 1) {
        val c0 = inside[y * width + x]
        val c1 = inside[y * width + x + 1]
        val c2 = inside[(y + 1) * width + x + 1]
        val c3 = inside[(y + 1) * width + x]

        if (valid != null && !(valid[y * width + x] && valid[y * width + x + 1] &&
              valid[(y + 1) * width + x + 1] && valid[(y + 1) * width + x])
        ) {
          continue
        }

        var case = 0
        if (c0) case = case or 1
        if (c1) case = case or 2
        if (c2) case = case or 4
        if (c3) case = case or 8
        if (case == 0 || case == 15) continue

        val bottom = y * width + x
        val top = (y + 1) * width + x
        val left = horizontalEdges + y * width + x
        val right = horizontalEdges + y * width + x + 1

        when (case) {
          1, 14 -> emit(bottom, left)
          2, 13 -> emit(bottom, right)
          3, 12 -> emit(left, right)
          4, 11 -> emit(right, top)
          6, 9 -> emit(bottom, top)
          7, 8 -> emit(top, left)

          // The two ambiguous cases, resolved by the middle of the square: whichever way the centre goes, the
          // two corners that share its state are the ones that join. Deciding it by the field rather than by a
          // fixed convention is what stops a narrow neck coming out joined on one grid and split on the next.
          5, 10 -> {
            val centre = (field[y * width + x] + field[y * width + x + 1] +
                field[(y + 1) * width + x + 1] + field[(y + 1) * width + x]) * 0.25
            val centreInside = centre < 0.0

            if ((case == 5) == centreInside) {
              emit(bottom, right)
              emit(top, left)
            } else {
              emit(bottom, left)
              emit(right, top)
            }
          }
        }
      }
    }

    return chain(segA, segB, incident, vx, vy)
  }

  /**
   * Walks the segment graph into contours.
   *
   * Open chains are drained first, from their loose ends. Doing it the other way round would start a walk in the
   * middle of an open chain, reach one end, and leave the other half behind as a second contour - a coastline
   * running off the edge of the grid would come back as two.
   */
  private fun chain(
    segA: List<Int>,
    segB: List<Int>,
    incident: Map<Int, MutableList<Int>>,
    vx: List<Double>,
    vy: List<Double>
  ): List<Contour> {
    val used = BooleanArray(segA.size)
    val out = ArrayList<Contour>()

    fun walk(start: Int): Contour {
      val path = ArrayList<Int>()
      path.add(start)

      var current = start
      var previousSegment = -1

      while (true) {
        val next = incident[current]?.firstOrNull { it != previousSegment && !used[it] } ?: break
        used[next] = true
        current = if (segA[next] == current) segB[next] else segA[next]
        previousSegment = next

        if (current == start) break
        path.add(current)
      }

      val closed = current == start && path.size > 2
      return Contour(
        DoubleArray(path.size) { vx[path[it]] },
        DoubleArray(path.size) { vy[path[it]] },
        closed
      )
    }

    // Loose ends first: a vertex touched by exactly one segment is the end of an open chain.
    for ((vertex, segments) in incident.entries.sortedBy { it.key }) {
      if (segments.size == 1 && !used[segments[0]]) {
        out.add(walk(vertex))
      }
    }

    // Then whatever is left, which is closed loops. Walked in segment order so the result is a function of the
    // grid rather than of how a hash map happened to iterate.
    //
    // The starting segment is deliberately *not* marked used first: the walk has to be able to come back along
    // it, because arriving at the vertex it started from is the only thing that tells a loop it is closed. An
    // earlier version marked it, and every ring in the world came back as an open chain missing its last edge.
    for (segment in segA.indices) {
      if (used[segment]) continue
      out.add(walk(segA[segment]))
    }

    return out.filter { it.size >= 2 }
  }
}
