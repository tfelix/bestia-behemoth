package net.bestia.worldgen.vector

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Polygon operations on **convex** vertex rings, for the town-block layout.
 *
 * ### Why convex is the whole design, and not a limitation to apologise for
 *
 * Each of these operations is a handful of lines *because* the input is convex, and the general versions are a
 * clipping library. A half-plane clip of a convex polygon is one pass with no bookkeeping, because the result is
 * a single connected piece - a concave polygon can be cut into several, and then every caller has to decide what
 * to do with the pieces. An inward offset of a convex polygon is the intersection of offset half-planes, which is
 * again one pass; the general case needs the straight skeleton to know where an edge collapses.
 *
 * The consumer is a Voronoi partition of a town into patches, and **a Voronoi cell is convex by construction**,
 * as is every piece cut off one by a straight line. So convexity is not an assumption imposed on the data - it is
 * a property the data already has, and stating it here is what buys the simplicity.
 *
 * ### These work on `List<Vec2d>`, not on `Ring`
 *
 * [Ring] is the *stored* form of a closed shape, and it validates accordingly: simple, at most
 * [Ring.MAX_VERTICES], above a minimum area, normalised winding. A subdivision produces a great many intermediate
 * polygons that are never stored and some that are degenerate and get discarded, so paying that validation on
 * each one would be both wasted and wrong - a sliver cut that a recursion is about to reject would throw instead.
 * Ring construction belongs at the end, on the pieces that survive.
 *
 * Winding is **counter-clockwise** throughout, matching what `Districts.convexHull` already returns.
 */
internal object ConvexPolygons {

  /** Below this a piece is rounding error rather than a shape, in square metres. Matches `Ring`'s own floor. */
  const val MIN_AREA = 1.0

  /** Twice the signed area. Positive for counter-clockwise. */
  fun signedArea2(polygon: List<Vec2d>): Double {
    if (polygon.size < 3) return 0.0
    var sum = 0.0
    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      sum += a.x * b.y - b.x * a.y
    }
    return sum
  }

  fun area(polygon: List<Vec2d>): Double = abs(signedArea2(polygon)) * 0.5

  fun perimeter(polygon: List<Vec2d>): Double {
    if (polygon.size < 2) return 0.0
    var sum = 0.0
    for (i in polygon.indices) sum += polygon[i].distanceTo(polygon[(i + 1) % polygon.size])
    return sum
  }

  fun centroid(polygon: List<Vec2d>): Vec2d {
    val area2 = signedArea2(polygon)
    // A degenerate piece has no meaningful centroid, and the vertex mean is the answer that cannot divide by
    // zero. Callers reject on area anyway; this only has to not produce a NaN on the way there.
    if (abs(area2) < 1e-9) {
      if (polygon.isEmpty()) return Vec2d.ZERO
      return Vec2d(polygon.sumOf { it.x } / polygon.size, polygon.sumOf { it.y } / polygon.size)
    }

    var cx = 0.0
    var cy = 0.0
    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      val cross = a.x * b.y - b.x * a.y
      cx += (a.x + b.x) * cross
      cy += (a.y + b.y) * cross
    }
    return Vec2d(cx / (3.0 * area2), cy / (3.0 * area2))
  }

  /**
   * Isoperimetric quotient: `4*pi*area / perimeter^2`. One for a circle, lower for anything else.
   *
   * How round a patch is, which is what decides whether a castle can sit on it: a keep and its bailey want a
   * compact site, and a long thin patch is a strip of street frontage rather than a place to fortify.
   */
  fun compactness(polygon: List<Vec2d>): Double {
    val p = perimeter(polygon)
    if (p <= 0.0) return 0.0
    return (4.0 * Math.PI * area(polygon)) / (p * p)
  }

  /**
   * The part of [polygon] on the inner side of a line, by Sutherland-Hodgman.
   *
   * The line is given as a point on it and a [normal]; the half-plane kept is the one the normal points *away*
   * from, i.e. `(v - through) dot normal <= 0`. Returns an empty list when nothing survives.
   *
   * One pass with no case analysis beyond in/out per vertex, which is what a convex subject buys: the result is
   * always one connected piece, so there is never a set of fragments to reassemble.
   */
  fun clipByHalfPlane(polygon: List<Vec2d>, through: Vec2d, normal: Vec2d): List<Vec2d> {
    if (polygon.size < 3) return emptyList()

    val out = ArrayList<Vec2d>(polygon.size + 1)
    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      val da = (a - through) dot normal
      val db = (b - through) dot normal

      if (da <= 0.0) out.add(a)
      // Strictly opposite signs only: a vertex exactly on the line is already emitted by the test above, and
      // emitting the crossing as well would duplicate it and leave a zero-length edge in the result.
      if ((da < 0.0 && db > 0.0) || (da > 0.0 && db < 0.0)) {
        out.add(a.lerp(b, da / (da - db)))
      }
    }

    return if (out.size >= 3) out else emptyList()
  }

  /**
   * [polygon] moved inward by a distance per edge, as the intersection of the offset half-planes.
   *
   * `distanceFor` is called with the index of each edge - the one from vertex `i` to vertex `i+1` - so a caller
   * can inset by a different amount per edge. That is the point of the parameter rather than a scalar: a block
   * fronting an arterial street on one side and an alley on the other is set back further from the artery, and
   * that asymmetry is a large part of what makes a subdivided block read as a real one.
   *
   * Successive half-plane clips rather than corner arithmetic, because clipping is exact at the corners for free.
   * Offsetting vertices along angle bisectors instead needs a separate collapse test per corner, and gets it
   * wrong on a sharp one - which is where a Voronoi cell most often has its corners.
   */
  fun inset(polygon: List<Vec2d>, distanceFor: (edge: Int) -> Double): List<Vec2d> {
    if (polygon.size < 3) return emptyList()

    var current = polygon
    for (i in polygon.indices) {
      val distance = distanceFor(i)
      if (distance <= 0.0) continue

      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      val edge = b - a
      if (edge.lengthSquared < 1e-12) continue

      // Outward normal of a counter-clockwise ring is the edge turned clockwise. Keeping the side the normal
      // points away from therefore keeps the interior, and moving the line inward by `distance` is the inset.
      val outward = Vec2d(edge.y, -edge.x).normalized()
      current = clipByHalfPlane(current, a - outward * distance, outward)
      if (current.size < 3) return emptyList()
    }

    return current
  }

  /** [inset] by the same distance on every edge. */
  fun insetAll(polygon: List<Vec2d>, distance: Double): List<Vec2d> = inset(polygon) { distance }

  /** Two halves of a polygon, or null when the cut produced nothing usable. */
  class Halves(val left: List<Vec2d>, val right: List<Vec2d>)

  /**
   * Cuts [polygon] across its longest edge, leaving a gap between the halves.
   *
   * **This one operation is what makes a subdivided block look like a block.** Splitting on the longest edge
   * means each cut runs across the piece's narrow way, so repeated cuts produce a row of plots sharing an axis
   * rather than an ever-finer pinwheel - and because the axis is the piece's own longest edge, the row is
   * parallel to the street the piece fronts. That is the whole reason houses in one block face the same way and
   * houses in the next block do not.
   *
   * @param ratio where along the longest edge to cut, in `(0,1)`. Near a half; a caller rolls it.
   * @param skew radians to turn the cut away from the edge's perpendicular. Zero gives a rectilinear grid of
   *   plots, and increasing it is what turns a planned quarter into an organic one - the single dial that
   *   distinguishes the two, which is why `TownLayout` becomes a bias on it rather than a second algorithm.
   * @param gap metres of alley left between the two halves, split evenly either side of the cut.
   */
  fun bisectLongestEdge(polygon: List<Vec2d>, ratio: Double, skew: Double, gap: Double): Halves? {
    if (polygon.size < 3) return null

    var longest = -1
    var longestLength = 0.0
    for (i in polygon.indices) {
      val length = polygon[i].distanceTo(polygon[(i + 1) % polygon.size])
      if (length > longestLength) {
        longestLength = length
        longest = i
      }
    }
    if (longest < 0 || longestLength <= 0.0) return null

    val a = polygon[longest]
    val b = polygon[(longest + 1) % polygon.size]
    val through = a.lerp(b, ratio.coerceIn(0.05, 0.95))

    // The cut's own direction is across the longest edge; the plane separating the halves therefore has the
    // edge's direction as its normal, turned by the skew.
    val along = (b - a).normalized()
    val normal = Vec2d(
      along.x * cos(skew) - along.y * sin(skew),
      along.x * sin(skew) + along.y * cos(skew)
    )

    val half = gap * 0.5
    val left = clipByHalfPlane(polygon, through - normal * half, normal)
    val right = clipByHalfPlane(polygon, through + normal * half, -normal)

    if (left.size < 3 || right.size < 3) return null
    if (area(left) < MIN_AREA || area(right) < MIN_AREA) return null
    return Halves(left, right)
  }

  /**
   * The largest axis-aligned-in-its-own-frame rectangle fitted to a polygon, as a plot would be.
   *
   * A `Lot` is an oriented rectangle - `centre`, `inwards`, a half-frontage and a half-depth - and a subdivided
   * block leaf is a convex polygon with four-ish sides. This is the reduction between them: take the leaf's
   * longest edge as the frontage direction, then measure the extent of the leaf along and across it.
   *
   * The extents are the leaf's own bounding box in that frame, so the rectangle can stick out of a leaf that is
   * genuinely triangular. That is left to the caller: the plot-overlap index is what actually keeps buildings
   * apart, and it is measured on the rectangles rather than on the leaves.
   */
  class Oriented(val centre: Vec2d, val along: Vec2d, val halfAlong: Double, val halfAcross: Double)

  fun orientedExtent(polygon: List<Vec2d>): Oriented? {
    if (polygon.size < 3) return null

    var longest = -1
    var longestLength = 0.0
    for (i in polygon.indices) {
      val length = polygon[i].distanceTo(polygon[(i + 1) % polygon.size])
      if (length > longestLength) {
        longestLength = length
        longest = i
      }
    }
    if (longest < 0) return null

    val along = (polygon[(longest + 1) % polygon.size] - polygon[longest]).normalized()
    if (along.lengthSquared < 0.5) return null
    val across = along.perpendicular()

    val centre = centroid(polygon)
    var minAlong = Double.MAX_VALUE
    var maxAlong = -Double.MAX_VALUE
    var minAcross = Double.MAX_VALUE
    var maxAcross = -Double.MAX_VALUE
    for (v in polygon) {
      val d = v - centre
      val u = d dot along
      val w = d dot across
      if (u < minAlong) minAlong = u
      if (u > maxAlong) maxAlong = u
      if (w < minAcross) minAcross = w
      if (w > maxAcross) maxAcross = w
    }

    // Re-centred on the extent's own middle rather than left on the centroid: a triangular leaf's centroid sits a
    // third of the way up it, and a rectangle centred there hangs off one end.
    val middle = centre + along * ((minAlong + maxAlong) * 0.5) + across * ((minAcross + maxAcross) * 0.5)
    val halfAlong = (maxAlong - minAlong) * 0.5
    val halfAcross = (maxAcross - minAcross) * 0.5
    if (halfAlong <= 0.0 || halfAcross <= 0.0) return null

    return Oriented(middle, along, halfAlong, halfAcross)
  }

  /**
   * A regular polygon of [radius] about [centre], counter-clockwise.
   *
   * The seed shape a Voronoi cell is clipped out of. A square would do - the cell is the intersection of
   * half-planes and any convex start that contains the cell gives the same answer - but a many-sided polygon
   * keeps the *unbounded* cells at the edge of a point set from coming back as huge slivers with corners in the
   * far distance, which is what a bounding rectangle does when a site sits near its edge.
   */
  fun regular(centre: Vec2d, radius: Double, sides: Int): List<Vec2d> {
    require(sides >= 3) { "a regular polygon needs at least three sides, was $sides" }
    val out = ArrayList<Vec2d>(sides)
    for (i in 0 until sides) {
      val theta = i * 2.0 * Math.PI / sides
      out.add(Vec2d(centre.x + cos(theta) * radius, centre.y + sin(theta) * radius))
    }
    return out
  }

  /**
   * Drops the vertices that a straight-ish corner contributes nothing to.
   *
   * Repeated half-plane clipping leaves collinear vertices behind - a cut that grazes a corner adds a point a
   * millimetre off the edge it lies on - and those cost a `Ring` its vertex budget for no shape. Measured as the
   * area of the triangle a vertex makes with its neighbours, which is the same test `Districts.simplify` uses to
   * fit a hull inside `Ring.MAX_VERTICES`.
   */
  fun clean(polygon: List<Vec2d>, minCornerArea: Double = 0.05): List<Vec2d> {
    if (polygon.size <= 3) return polygon

    val kept = ArrayList<Vec2d>(polygon.size)
    for (i in polygon.indices) {
      val previous = polygon[(i - 1 + polygon.size) % polygon.size]
      val next = polygon[(i + 1) % polygon.size]
      val cross = (polygon[i].x - previous.x) * (next.y - previous.y) -
          (polygon[i].y - previous.y) * (next.x - previous.x)
      if (abs(cross) * 0.5 >= minCornerArea) kept.add(polygon[i])
    }

    return if (kept.size >= 3) kept else polygon
  }

  /** Longest distance from [centre] to any vertex. The reach of a shape about a point. */
  fun reachFrom(polygon: List<Vec2d>, centre: Vec2d): Double =
    polygon.maxOfOrNull { it.distanceTo(centre) } ?: 0.0

  /**
   * Whether a point is inside a counter-clockwise convex polygon.
   *
   * Every edge cross product on the same side, which is O(n) with no ray casting and no parity. [Ring.contains]
   * is the general test and goes through fixed point because two chunks must agree on it; nothing here is stored
   * or re-derived per chunk, so this stays in floating point and stays cheap.
   */
  fun contains(polygon: List<Vec2d>, point: Vec2d): Boolean {
    if (polygon.size < 3) return false
    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      if ((b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x) < 0.0) return false
    }
    return true
  }

  /** Distance from a point to the polygon's boundary, positive inside and negative outside. */
  fun signedDistance(polygon: List<Vec2d>, point: Vec2d): Double {
    if (polygon.size < 3) return 0.0
    var nearest = Double.MAX_VALUE
    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      val ab = b - a
      val lengthSq = ab.lengthSquared
      val t = if (lengthSq <= 0.0) 0.0 else (((point - a) dot ab) / lengthSq).coerceIn(0.0, 1.0)
      nearest = minOf(nearest, point.distanceTo(a + ab * t))
    }
    return if (contains(polygon, point)) nearest else -nearest
  }

  /** Half the shortest width of a polygon, measured as the largest inscribed distance from the boundary. */
  fun inradiusAt(polygon: List<Vec2d>, at: Vec2d): Double = maxOf(0.0, signedDistance(polygon, at))

  /**
   * Scales a polygon about a point, keeping its shape.
   *
   * On a convex polygon this cannot self-intersect: the vertices keep their angular order about an interior
   * point and only their radii change - the same argument `Districts.ringAround` makes for pushing a hull out.
   */
  fun scaledAbout(polygon: List<Vec2d>, centre: Vec2d, factor: Double): List<Vec2d> =
    polygon.map { centre + (it - centre) * factor }

  /** Whether every vertex turns the same way. A guard for the assumption the rest of this file rests on. */
  fun isConvex(polygon: List<Vec2d>): Boolean {
    if (polygon.size < 3) return false
    var sign = 0.0
    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      val c = polygon[(i + 2) % polygon.size]
      val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
      if (abs(cross) < 1e-9) continue
      if (sign == 0.0) sign = cross
      else if (cross * sign < 0.0) return false
    }
    return true
  }

  /** Equivalent radius of a disc with the same area. What "how big is this patch" means for a count. */
  fun equivalentRadius(polygon: List<Vec2d>): Double = sqrt(area(polygon) / Math.PI)
}
