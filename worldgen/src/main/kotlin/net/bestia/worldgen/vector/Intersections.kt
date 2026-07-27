package net.bestia.worldgen.vector

/**
 * Where two polylines cross, in world space.
 *
 * The architecture document asks for road-river crossings to be detected here, at vector level, rather than
 * at chunk level - and the reason is the usual one. A crossing found from geometry is *one* world position
 * that every chunk agrees on, so the two chunks either side of a bridge agree that the bridge exists and
 * where its abutments are. A crossing found per chunk by noticing that a road cell and a river cell coincide
 * would be found twice, at two slightly different places, by chunks that cannot talk to each other.
 */
object Intersections {

  /** One crossing: where it is, and how far along each line. */
  data class Crossing(
    val point: Vec2d,
    /** Arc length along the first line. */
    val sA: Double,
    /** Arc length along the second line. */
    val sB: Double
  )

  /**
   * Every point where [a] and [b] cross.
   *
   * Brute force over segment pairs, guarded by a bounding-box test. That is adequate because the caller has
   * already narrowed the candidates - a road is tested against the rivers whose bounds it overlaps, which is
   * a handful - and because being obviously correct matters more here than being fast: a missed crossing is
   * a road running through a river, and a spurious one is a bridge over dry land.
   */
  fun of(a: Polyline, b: Polyline): List<Crossing> {
    if (!a.bbox.intersects(b.bbox)) return emptyList()

    val out = ArrayList<Crossing>()

    for (i in 0 until a.segmentCount) {
      val a0 = a.points[i]
      val a1 = a.points[i + 1]

      for (j in 0 until b.segmentCount) {
        val b0 = b.points[j]
        val b1 = b.points[j + 1]

        val hit = segmentCrossing(a0, a1, b0, b1) ?: continue
        out.add(
          Crossing(
            point = hit.first,
            sA = a.arcLengthAt(i) + hit.second * a0.distanceTo(a1),
            sB = b.arcLengthAt(j) + hit.third * b0.distanceTo(b1)
          )
        )
      }
    }

    return out
  }

  /**
   * Where two segments cross, with the parameter along each.
   *
   * @return `(point, tA, tB)` or null when they do not cross
   */
  fun segmentCrossing(
    a0: Vec2d,
    a1: Vec2d,
    b0: Vec2d,
    b1: Vec2d
  ): Triple<Vec2d, Double, Double>? {
    val da = a1 - a0
    val db = b1 - b0
    val denominator = da cross db

    // Parallel or degenerate. Collinear overlap is deliberately not reported: a road running *along* a river
    // for a kilometre is not a crossing, and calling it one would put a bridge in the middle of it.
    if (denominator == 0.0) return null

    val offset = b0 - a0
    val tA = (offset cross db) / denominator
    val tB = (offset cross da) / denominator

    if (tA < 0.0 || tA > 1.0 || tB < 0.0 || tB > 1.0) return null

    return Triple(a0 + da * tA, tA, tB)
  }
}
