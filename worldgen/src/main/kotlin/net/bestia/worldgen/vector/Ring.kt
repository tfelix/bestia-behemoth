package net.bestia.worldgen.vector

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.fields.Noise
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The result of projecting a world position onto a [Ring].
 *
 * The closed-curve counterpart of [Projection], and it is shorter by exactly one field: there is no
 * `beyondEnd`, because a ring has no ends. That absence is the point of the type. Every profile written
 * against a [Polyline] has to decide what to do off the end of the line, and every one of those decisions
 * is a bug waiting at the seam of a closed curve, where "off the end" would mean "back at the start".
 */
data class RingProjection(
  /** Arc length of the closest point from vertex 0, in metres, in `[0, perimeter)`. */
  val s: Double,
  /**
   * The same location in station parameter space: `segment + t`, in `[0, vertexCount)`.
   *
   * Vertex `i` sits at exactly `i`, and the value wraps rather than clamping - which is why the table it
   * indexes has to be a *periodic* [StationTable]. Sampling a clamped table here would make every channel
   * flat across the segment from the last vertex back to the first.
   */
  val u: Double,
  /** Unsigned distance to the ring's boundary. Zero on the boundary, positive both inside and outside. */
  val distance: Double,
  /** The closest point on the boundary. */
  val point: Vec2d,
  /** Index of the segment carrying the closest point, in `[0, vertexCount)`. */
  val segment: Int
)

/**
 * A simple closed polygon in world space: the vector tier's areal geometry.
 *
 * ### Why this is not a `Polyline` with the first point repeated
 *
 * That was the obvious implementation and it is wrong in six specific places, not one general one.
 * [Polyline] is *documented* as open and its whole surface is built on that: `project` returns
 * [Projection.beyondEnd], which is meaningless here; `arcLengthAt` runs `0..length` with no wrap, so a
 * position just clockwise of vertex 0 gets an arc length near the perimeter instead of near zero;
 * `lateral` is left-of-tangent per segment and flips sign at a reflex vertex, so it cannot say
 * inside-from-outside; `chaikin` and `resample` pin the endpoints, which on a closed curve pins an
 * arbitrary vertex and puts a corner in it; `offsetLaterally` special-cases the two ends rather than
 * averaging across the seam; and the two-distinct-points precondition is not the precondition a ring
 * needs. Adding a `closed` flag would mean six conditionals inside a class whose contract says otherwise,
 * and the failures would all be at the seam - one vertex out of sixty-four - which is exactly the kind of
 * defect that survives a unit test and shows up as one wrong pixel on a lake shore.
 *
 * So the ring never enters that contract. The closing vertex is implicit, indices go through
 * `Math.floorMod`, arc length is taken modulo the perimeter, and [asPolyline] exists for the one direction
 * that *is* safe: handing a closed point list to a viewer that only wants to draw it.
 *
 * ### Why a vertex ring rather than a radial `r(theta)`
 *
 * A radial function is cheaper - containment is one compare instead of a crossing count - and it can
 * express a warped disc, which is what the first two producers want. It cannot express an oxbow lake. A
 * crescent is not star-shaped about any interior point, so some ray from the centre crosses the boundary
 * four times and no single-valued `r(theta)` exists. Choosing the radial form would have meant discovering
 * that at the third producer and rewriting the type with two producers already built on it. The cost is
 * modest in context: [PolylineFeature] already projects a column against every segment of a hundred-vertex
 * river centerline, and a ring is capped at [MAX_VERTICES].
 *
 * ### What `init` refuses, and why self-intersection is on the list
 *
 * A self-intersecting ring has no interior - the crossing-number rule still returns an answer for every
 * point, but the answer depends on which way the loops wind and is not the shape anyone drew. Since
 * [contains] is the entire reason this type exists, a ring that makes it ill-defined must not be
 * constructible. The check is O(n^2) over at most [MAX_VERTICES] segments, paid once at construction, for
 * a type built a few hundred times per world.
 *
 * Every producer must wrap construction in `runCatching` - the lesson `TownStage.wallStretches` already
 * encodes. A ring is built from noise, and noise occasionally folds a shape over itself; a world that
 * refuses to generate because one pond in nine hundred came out as a figure eight is a worse outcome than
 * that pond not existing.
 */
class Ring(vertices: List<Vec2d>) {

  /** The vertices, counter-clockwise, with no repeated closing vertex and no duplicate neighbours. */
  val vertices: List<Vec2d>

  /** Cumulative arc length at each vertex; `cumulative[0] == 0.0`. Length `vertexCount + 1`, wrap included. */
  private val cumulative: DoubleArray

  /**
   * Vertices in fixed point, absolute - **not** relative to any origin.
   *
   * `Quantize.toFixed` is applied to the absolute world coordinate, and every subtraction [contains] needs
   * then happens between two `Long`s, which is exact. So there is no rounding anywhere in the decision
   * path: not one rounding that two callers might do differently, but none at all.
   *
   * The stronger claim - that quantising the absolute value rather than the difference is *what makes two
   * chunks agree* - is one the fails-first pass refused to support, and it is worth recording why rather
   * than quietly keeping the code and dropping the sentence. Rewriting this as
   * `toFixed(x - vertex.x)` turned no test red, and inspection says it should not have: both forms are
   * pure functions of the query position, so two chunks handed the same position agree under either. What
   * the absolute form buys is exactness, not agreement. The property that buys agreement is the one
   * `RingTest."containment is a function of the quantised position"` actually tests - that the answer
   * depends on the quantised pair and on nothing else - and that is a property of *every* branch in
   * [contains], which is how the raw-double bbox guard that used to sit at the top of it was found.
   */
  private val fx: LongArray
  private val fy: LongArray

  val perimeter: Double
  val bbox: Aabb

  /** Signed area, positive by construction since the winding is normalised counter-clockwise. */
  val area: Double

  val centroid: Vec2d

  /**
   * The two cheap rejects [contains] runs before counting crossings, **also in fixed point**.
   *
   * They have to be. The crossing count is a pure function of the quantised query position, so if a guard
   * in front of it branches on the raw doubles then `contains` as a whole is not, and the guard is where
   * two chunks would disagree. That is not hypothetical: it was found by breaking the edge rule during the
   * fails-first pass and watching the *quantisation* test go red rather than the one aimed at the edge
   * rule, which can only happen if something upstream of the count is looking at unquantised values.
   *
   * The disc is the bounding circle about [centroid] and can only *reject*: every edge lies inside it,
   * because a segment between two points of a disc stays in the disc. There is deliberately no matching
   * inner disc to accept with - the area centroid of a crescent sits in the bite, outside the ring
   * entirely, so "close to the centroid" does not imply "inside".
   */
  private val minFx: Long
  private val maxFx: Long
  private val minFy: Long
  private val maxFy: Long
  private val centroidFx: Long
  private val centroidFy: Long
  private val outerRadiusSqFixed: Long

  init {
    val cleaned = ArrayList<Vec2d>(vertices.size)
    for (p in vertices) {
      if (cleaned.isEmpty() || cleaned.last() != p) cleaned.add(p)
    }
    // The wrap pair is a duplicate like any other: a caller that closed the list by repeating vertex 0
    // gets that vertex dropped rather than a zero-length segment across the seam.
    while (cleaned.size >= 2 && cleaned.first() == cleaned.last()) cleaned.removeAt(cleaned.size - 1)

    require(cleaned.size >= 3) {
      "A ring needs at least three distinct vertices including the wrap pair, got ${cleaned.size}"
    }
    require(cleaned.size <= MAX_VERTICES) {
      "A ring is capped at $MAX_VERTICES vertices, got ${cleaned.size}; the self-intersection check is " +
          "quadratic and a shape this detailed belongs in the raster tier"
    }

    val signed = signedAreaOf(cleaned)
    require(abs(signed) > MIN_AREA) {
      "A ring needs a non-degenerate interior, area was ${"%.4f".format(Locale.ROOT, signed)} m2"
    }

    // Counter-clockwise, always. Every downstream sign convention - the crossing-number rule, the outward
    // normal, the order `outline()` hands to the viewer - depends on the winding, and normalising here is
    // the one place it can be guaranteed rather than asked for.
    this.vertices = if (signed < 0.0) cleaned.reversed() else cleaned
    this.area = abs(signed)

    require(!selfIntersects(this.vertices)) {
      "A ring must be simple; this one crosses itself and would have no well-defined interior"
    }

    val n = this.vertices.size
    this.cumulative = DoubleArray(n + 1)
    for (i in 1..n) {
      cumulative[i] = cumulative[i - 1] + this.vertices[i - 1].distanceTo(this.vertices[i % n])
    }
    this.perimeter = cumulative[n]
    this.bbox = Aabb.around(this.vertices)

    require(bbox.width <= MAX_EXTENT && bbox.height <= MAX_EXTENT) {
      "A ring is capped at ${(MAX_EXTENT / 1000).toInt()} km per axis so the fixed-point arithmetic in " +
          "contains() cannot overflow, was ${bbox.width.toInt()}x${bbox.height.toInt()} m"
    }

    this.fx = LongArray(n) { Quantize.toFixed(this.vertices[it].x) }
    this.fy = LongArray(n) { Quantize.toFixed(this.vertices[it].y) }

    this.minFx = fx.min()
    this.maxFx = fx.max()
    this.minFy = fy.min()
    this.maxFy = fy.max()

    // Area-weighted centroid, not the vertex mean: the mean drifts towards whichever arc happens to carry
    // more vertices, which for a warped circle is wherever the warp put them.
    //
    // `area`, not `signed`. They differ exactly when the caller wound the ring clockwise, and passing the
    // pre-normalisation `signed` there divided the cross sums of the *reversed* vertices by a negative
    // number - reflecting the centroid through the origin, hundreds of kilometres out. Every test here used
    // a counter-clockwise fixture and passed; what found it was the first producer whose shapes happened to
    // come out clockwise, and it found it as "no ponds anywhere in the world".
    this.centroid = centroidOf(this.vertices, this.area)
    this.centroidFx = Quantize.toFixed(centroid.x)
    this.centroidFy = Quantize.toFixed(centroid.y)

    var outer = 0L
    for (i in 0 until n) {
      val dx = fx[i] - centroidFx
      val dy = fy[i] - centroidFy
      val d = dx * dx + dy * dy
      if (d > outer) outer = d
    }
    this.outerRadiusSqFixed = outer
  }

  val vertexCount get() = vertices.size

  /** Segment count equals vertex count: the last segment runs from the last vertex back to the first. */
  val segmentCount get() = vertices.size

  fun vertex(index: Int): Vec2d = vertices[Math.floorMod(index, vertices.size)]

  /** Arc length at vertex [index], wrapped. */
  fun arcLengthAt(index: Int): Double = cumulative[Math.floorMod(index, vertices.size)]

  /**
   * Whether the world position lies inside the ring, decided **entirely in integers**.
   *
   * The rule is the crossing number with a half-open edge test: an edge counts when exactly one of its
   * endpoints is strictly above the query row, so a ray passing exactly through a vertex is counted once
   * rather than twice or zero times, and a query point sitting exactly on a horizontal edge is resolved
   * the same way from either side. There is no epsilon anywhere in it, and there could not usefully be
   * one: the whole purpose is that two chunks that quantise the same column centre take the same branch,
   * and an epsilon is a second decision that would have to agree as well.
   *
   * **Every guard in front of the count is in fixed point too**, so the whole function is a pure function
   * of `(toFixed(x), toFixed(y))` and nothing else. A bbox test on the raw doubles would be a float branch
   * one line above a paragraph promising there are none, and near a vertex - where the bbox edge and the
   * ring boundary touch - it is a branch two chunks can genuinely take differently.
   *
   * Relative coordinates are for overflow rather than precision. At millimetre fixed point the products
   * below are two coordinate *differences* multiplied together, both bounded by [MAX_EXTENT], which is
   * where that cap comes from: 100 km is 1e8 units, so a product is at most 1e16 and a sum of two at most
   * 2e16, comfortably inside a `Long`'s 9.2e18. `AreaFeature.MAX_AREA_EXTENT` is far tighter and exists
   * for an unrelated reason - the spatial index - so in practice **the binding constraint on how large a
   * ring may be is the index, not this arithmetic.**
   */
  fun contains(x: Double, y: Double): Boolean {
    val qx = Quantize.toFixed(x)
    val qy = Quantize.toFixed(y)

    if (qx < minFx || qx > maxFx || qy < minFy || qy > maxFy) return false

    // One squared compare, and it can only reject. Every edge lies inside the bounding disc because a
    // segment between two points of a disc stays in it, so beyond the disc is beyond the ring.
    val dxc = qx - centroidFx
    val dyc = qy - centroidFy
    if (dxc * dxc + dyc * dyc > outerRadiusSqFixed) return false

    var inside = false
    val n = vertices.size
    var j = n - 1
    for (i in 0 until n) {
      val yi = fy[i]
      val yj = fy[j]
      // Half-open in y: the upper endpoint of an edge belongs to it, the lower one does not.
      if ((yi > qy) != (yj > qy)) {
        // Sign of the cross product of (edge) x (endpoint -> query), which says which side of the edge
        // the query point is on. Exact in Long; the operands are bounded as the KDoc above argues.
        val cross = (fx[j] - fx[i]) * (qy - yi) - (fy[j] - yi) * (qx - fx[i])
        // Crossing to the right of the query point. The comparison direction depends on which way the
        // edge runs in y, which is what makes this correct for both upward and downward edges.
        if ((cross > 0L) == (yj > yi)) inside = !inside
      }
      j = i
    }
    return inside
  }

  fun contains(p: Vec2d): Boolean = contains(p.x, p.y)

  /**
   * Projects a world position onto the ring's boundary.
   *
   * Scanned in index order with a strict comparison, so ties break towards the lower segment and the
   * result never depends on iteration order. Same guarantee, and for the same reason, as
   * [Polyline.project].
   */
  fun project(p: Vec2d): RingProjection {
    var bestDistSq = Double.POSITIVE_INFINITY
    var bestSegment = 0
    var bestT = 0.0
    var bestX = vertices[0].x
    var bestY = vertices[0].y

    val n = vertices.size
    for (i in 0 until n) {
      val a = vertices[i]
      val b = vertices[(i + 1) % n]
      val abx = b.x - a.x
      val aby = b.y - a.y
      val lenSq = abx * abx + aby * aby
      // lenSq > 0: the constructor dropped duplicate neighbours, wrap pair included.
      var t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq
      t = if (t < 0.0) 0.0 else if (t > 1.0) 1.0 else t

      val qx = a.x + abx * t
      val qy = a.y + aby * t
      val dx = p.x - qx
      val dy = p.y - qy
      val distSq = dx * dx + dy * dy

      if (distSq < bestDistSq) {
        bestDistSq = distSq
        bestSegment = i
        bestT = t
        bestX = qx
        bestY = qy
      }
    }

    return RingProjection(
      s = cumulative[bestSegment] + bestT * (cumulative[bestSegment + 1] - cumulative[bestSegment]),
      u = bestSegment + bestT,
      distance = sqrt(bestDistSq),
      point = Vec2d(bestX, bestY),
      segment = bestSegment
    )
  }

  /**
   * Distance to the boundary, negative inside and positive outside.
   *
   * The magnitude is a plain double and is *not* quantised, because it feeds a profile and a blend weight -
   * a continuous quantity where a nanometre of disagreement between two chunks is a nanometre of terrain.
   * The **sign** comes from [contains] and is therefore an integer decision both chunks take identically.
   * The two meet cleanly: the sign flips exactly where the magnitude passes through zero, so the worst a
   * disagreement could do is move the flip by a millimetre, where the profile's own value is within a
   * millimetre times its slope of zero.
   */
  fun signedDistance(x: Double, y: Double): Double {
    val d = project(Vec2d(x, y)).distance
    return if (contains(x, y)) -d else d
  }

  /**
   * The ring as a closed [Polyline], for drawing only.
   *
   * The one safe direction across the two types: a viewer wants a point list it can stroke, and repeating
   * vertex 0 at the end is exactly how it should be handed one. Nothing in generation may go back the
   * other way - see the class KDoc for the six reasons.
   */
  fun asPolyline(): Polyline = Polyline(vertices + vertices.first())

  override fun toString() =
    "Ring[vertices=${vertices.size}, area=${"%.0f".format(Locale.ROOT, area)}m2, " +
        "perimeter=${"%.0f".format(Locale.ROOT, perimeter)}m]"

  companion object {

    /**
     * Vertex cap, and therefore the bound on the quadratic self-intersection check.
     *
     * Sixty-four vertices around a shape at most a few kilometres across is a vertex every hundred metres
     * or so, which is finer than the resolution anything downstream reads a lake shore at. A shape that
     * genuinely needs more detail than this is asking the vector tier to do the raster tier's job.
     */
    const val MAX_VERTICES = 64

    /** Below this a "ring" is three collinear points with rounding on them, not a shape. Square metres. */
    private const val MIN_AREA = 1.0

    /**
     * Hard cap per axis, in metres, so [contains]'s fixed-point products cannot overflow a `Long`.
     *
     * This is an arithmetic bound and nothing else - it is not the size a ring *should* be. The size a
     * ring should be is `AreaFeature.MAX_AREA_EXTENT`, which is an order of magnitude smaller and is set
     * from a measurement of the spatial index. Both exist because they answer different questions, and
     * merging them would tie a correctness limit to a performance one.
     */
    const val MAX_EXTENT = 100_000.0

    /**
     * A circle pushed around by fbm noise, which is what the first three producers all want.
     *
     * The noise is sampled **around a closed circle** in a two-dimensional field rather than as a function
     * of the angle, so it is periodic in theta by construction: theta and theta + 2pi are literally the
     * same sample point. Sampling `fbm(theta)` on a line instead would leave a step at the seam, which on
     * a lake shore is a visible notch always at the same compass bearing.
     *
     * The lesson underneath is one the module has already paid for elsewhere: a landform generated from
     * one number is shaped like that number's level set. A pond whose radius is a constant is a disc, and
     * a disc reads as a crater however carefully its depth profile is tuned.
     *
     * @param roughness fraction of [radius] the boundary may wander, in `[0,1)`
     * @param lobes how many times the noise repeats around the circle; low numbers give a few broad bays
     */
    fun warpedCircle(
      centre: Vec2d,
      radius: Double,
      seed: Long,
      vertexCount: Int = 24,
      roughness: Double = 0.28,
      lobes: Double = 2.0,
      octaves: Int = 3
    ): Ring {
      require(radius > 0.0) { "radius must be positive, was $radius" }
      require(vertexCount >= 3) { "a warped circle needs at least three vertices, was $vertexCount" }
      require(roughness >= 0.0 && roughness < 1.0) { "roughness must be in [0,1), was $roughness" }
      require(lobes > 0.0) { "lobes must be positive, was $lobes" }

      val noiseSeed = GenRng.hash(seed, WARP_SALT)
      val points = ArrayList<Vec2d>(vertexCount)
      for (i in 0 until vertexCount) {
        val theta = i * 2.0 * PI / vertexCount
        val sample = Noise.fbm(noiseSeed, cos(theta) * lobes, sin(theta) * lobes, octaves)
        val r = radius * (1.0 + roughness * sample)
        points.add(Vec2d(centre.x + cos(theta) * r, centre.y + sin(theta) * r))
      }
      return Ring(points)
    }

    /**
     * A lune: a disc with an equal disc bitten out of it, offset along [bearing].
     *
     * The shape an oxbow lake is, and the reason the vertex ring exists at all - no `r(theta)` can express
     * it. **It is built by walking the two arcs, not by sampling a radius**, and that distinction is the
     * whole content of this function: a radial construction can only ever emit a star-shaped ring, so the
     * first attempt here produced something crescent-*looking* whose boundary any radial type could have
     * drawn. `RingTest."a crescent is not star-shaped about its centroid"` is what caught it, by counting
     * how many times a ray from the centroid crosses the boundary - a shape that never manages more than
     * one crossing has not earned this type.
     *
     * Equal radii are not a simplification for its own sake. They make the two horns symmetric, they make
     * the inner arc's half-angle exactly `pi - alpha` so both arcs come off one angle, and they make [bite]
     * mean something a caller can reason about: the crescent's width along the bearing, as a fraction of
     * [radius].
     *
     * @param bearing direction from the crescent's centre towards the open side of the horns
     * @param bite width of the remaining crescent as a fraction of [radius]; small is thin
     */
    fun crescent(
      centre: Vec2d,
      radius: Double,
      bearing: Vec2d,
      bite: Double,
      seed: Long,
      vertexCount: Int = 28,
      roughness: Double = 0.10
    ): Ring {
      require(radius > 0.0) { "radius must be positive, was $radius" }
      require(bite > 0.1 && bite < 1.6) {
        "bite is the crescent's width over its radius and must be in (0.1,1.6), was $bite"
      }
      require(vertexCount >= 8) { "a crescent needs at least eight vertices, was $vertexCount" }

      val dir = bearing.normalized()
      require(dir.lengthSquared > 0.0) { "a crescent needs a bearing with a direction" }
      val side = dir.perpendicular()

      val offset = radius * bite
      val half = offset * 0.5
      // Half-chord of the two circles' intersection. Real because bite < 2.
      val chord = sqrt(radius * radius - half * half)
      // Angle from the outer centre to a horn tip. The outer arc is everything *except* the wedge
      // between the horns; the inner arc, with equal radii, spans exactly the complementary 2*alpha.
      val alpha = atan2(chord, half)

      val biteCentreX = centre.x + dir.x * offset
      val biteCentreY = centre.y + dir.y * offset

      val noiseSeed = GenRng.hash(seed, CRESCENT_SALT)
      // Split the vertices between the arcs in proportion to how much angle each carries.
      val outerSweep = 2.0 * PI - 2.0 * alpha
      val innerSweep = 2.0 * alpha
      val outerCount = max(3, (vertexCount * outerSweep / (outerSweep + innerSweep)).toInt())
      val innerCount = max(2, vertexCount - outerCount)

      fun arcPoint(cx: Double, cy: Double, angle: Double, r: Double) =
        Vec2d(
          cx + (dir.x * cos(angle) + side.x * sin(angle)) * r,
          cy + (dir.y * cos(angle) + side.y * sin(angle)) * r
        )

      val points = ArrayList<Vec2d>(outerCount + innerCount)

      // Outer arc, horn to horn the long way round, both tips included.
      for (i in 0 until outerCount) {
        val t = i.toDouble() / (outerCount - 1)
        val angle = alpha + t * outerSweep
        // Roughness tapered to zero at the tips with a half sine, so the horns stay exactly on the two
        // circles' intersection and the arcs cannot cross each other there.
        val taper = sin(t * PI)
        val wobble = 1.0 + roughness * taper *
            Noise.fbm(noiseSeed, cos(angle) * 2.0, sin(angle) * 2.0, 2)
        points.add(arcPoint(centre.x, centre.y, angle, radius * wobble))
      }

      // Inner arc, back from the far horn to the near one, tips excluded - they are already there.
      for (i in 1 until innerCount + 1) {
        val t = i.toDouble() / (innerCount + 1)
        val angle = (PI + alpha) - t * innerSweep
        val taper = sin(t * PI)
        // Inward wobble is halved: the inner arc is the concave side and a deep dent there is what would
        // pinch the crescent in two.
        val wobble = 1.0 - roughness * 0.5 * taper *
            Noise.fbm(noiseSeed + 1, cos(angle) * 2.0, sin(angle) * 2.0, 2)
        points.add(arcPoint(biteCentreX, biteCentreY, angle, radius * wobble))
      }

      return Ring(points)
    }

    /**
     * A ribbon: the area swept by a varying half-width along an open spine.
     *
     * What a pond in a glacial valley is - long, narrow and bent the way the valley bends - and the shape no
     * disc or lune can express, because its axis is a curve somebody else already computed. Reusing that
     * curve rather than fitting a shape to it is the point: a tarn behind a moraine sits in the trough the
     * trough feature already describes, so the pond and the valley cannot disagree about where the valley is.
     *
     * [halfWidthAt] is given the normalised position along the spine and **must return zero, or very nearly
     * zero, at both ends**, or the two flanks will not meet and the ring will be a blunt-ended sausage with
     * two coincident-but-not-equal vertex pairs at the caps. Tapering it is also the honest shape: a valley
     * pond narrows to nothing where the water finally meets the rising floor.
     *
     * Can fail. A spine that bends tighter than its own half-width folds the inner flank across itself, and
     * `init` rejects the result - which is correct and is why every caller wraps this in `runCatching`.
     */
    fun ribbon(
      spine: Polyline,
      vertexCount: Int = 22,
      widthAt: (t: Double, side: Int) -> Double
    ): Ring {
      require(vertexCount >= 4) { "a ribbon needs at least four spine samples, was $vertexCount" }

      val forward = ArrayList<Vec2d>(vertexCount)
      val backward = ArrayList<Vec2d>(vertexCount)
      for (i in 0 until vertexCount) {
        val t = i.toDouble() / (vertexCount - 1)
        val s = t * spine.length
        val at = spine.pointAt(s)
        val normal = spine.tangentAt(s).perpendicular()
        // The two flanks are asked separately, because the thing a ribbon usually follows is not symmetric
        // about its own axis - a valley has one steep side and one gentle one, and a lake in it reaches
        // further up the gentle one.
        val left = widthAt(t, 1)
        val right = widthAt(t, -1)
        forward.add(Vec2d(at.x + normal.x * left, at.y + normal.y * left))
        backward.add(Vec2d(at.x - normal.x * right, at.y - normal.y * right))
      }

      // Up one flank and back down the other. Where both widths taper to zero the caps coincide and `init`
      // drops the duplicates, giving a lens; where they do not, the cap is a straight edge, which is equally
      // valid and is what a pond pinched against a dam actually looks like.
      return Ring(forward + backward.reversed())
    }

    /**
     * A fan lobe: a wedge spreading from an apex, wider and rounder as it goes.
     *
     * What an alluvial fan and a delta both are - a cone of sediment opening downstream from the point the
     * flow leaves its confinement. The apex is a genuine point of the ring rather than a rounded end, since
     * that is the diagnostic shape.
     *
     * @param spread half-angle of the wedge in radians
     */
    fun fanLobe(
      apex: Vec2d,
      bearing: Vec2d,
      length: Double,
      spread: Double,
      seed: Long,
      vertexCount: Int = 20,
      roughness: Double = 0.16
    ): Ring {
      require(length > 0.0) { "length must be positive, was $length" }
      require(spread > 0.05 && spread < PI * 0.9) { "spread must be a sensible half-angle, was $spread" }
      require(vertexCount >= 5) { "a fan lobe needs at least five vertices, was $vertexCount" }

      val dir = bearing.normalized()
      require(dir.lengthSquared > 0.0) { "a fan lobe needs a bearing with a direction" }
      val side = dir.perpendicular()

      val noiseSeed = GenRng.hash(seed, FAN_SALT)
      val points = ArrayList<Vec2d>(vertexCount + 1)
      points.add(apex)
      for (i in 0 until vertexCount) {
        // Sweep from one horn round to the other along the fan's toe.
        val t = i.toDouble() / (vertexCount - 1)
        val angle = -spread + t * 2.0 * spread
        val wobble = 1.0 + roughness * Noise.fbm(noiseSeed, cos(angle * 2.0), sin(angle * 2.0), 2)
        // The toe is farthest straight ahead and pulls in towards the horns, which is what makes it a
        // lobe rather than a pie slice.
        val reach = length * wobble * (0.55 + 0.45 * cos(angle * PI * 0.5 / spread))
        val ax = dir.x * cos(angle) + side.x * sin(angle)
        val ay = dir.y * cos(angle) + side.y * sin(angle)
        points.add(Vec2d(apex.x + ax * reach, apex.y + ay * reach))
      }
      return Ring(points)
    }

    // Distinct salts so the three shape families never draw the same noise from the same seed - two
    // producers that happen to share a seed would otherwise emit congruent shapes.
    private const val WARP_SALT = 0x51A4_C17C_1E00L
    private const val CRESCENT_SALT = 0xC7E5_CE17L
    private const val FAN_SALT = 0xFA07_10BEL

    /** Twice the signed area: positive when the vertices wind counter-clockwise. */
    private fun signedAreaOf(points: List<Vec2d>): Double {
      var sum = 0.0
      val n = points.size
      for (i in 0 until n) {
        val a = points[i]
        val b = points[(i + 1) % n]
        sum += a.x * b.y - b.x * a.y
      }
      return sum * 0.5
    }

    private fun centroidOf(points: List<Vec2d>, signedArea: Double): Vec2d {
      if (abs(signedArea) < MIN_AREA) {
        // Unreachable through `init`, which rejects it, but a centroid that divides by zero is not the
        // way to find that out.
        return Vec2d(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size)
      }
      var cx = 0.0
      var cy = 0.0
      val n = points.size
      for (i in 0 until n) {
        val a = points[i]
        val b = points[(i + 1) % n]
        val cross = a.x * b.y - b.x * a.y
        cx += (a.x + b.x) * cross
        cy += (a.y + b.y) * cross
      }
      val scale = 1.0 / (6.0 * signedArea)
      return Vec2d(cx * scale, cy * scale)
    }

    /**
     * Whether any two non-adjacent edges cross.
     *
     * Adjacent edges are skipped because they share a vertex by definition and
     * [Intersections.segmentCrossing] would report that shared endpoint as a crossing. The wrap pair -
     * the last edge and the first - is adjacent too, and forgetting that is the classic way this check
     * rejects every ring it is given.
     */
    private fun selfIntersects(points: List<Vec2d>): Boolean {
      val n = points.size
      for (i in 0 until n) {
        val a0 = points[i]
        val a1 = points[(i + 1) % n]
        // Start at i + 2: edge i + 1 shares a vertex with edge i.
        for (j in i + 2 until n) {
          // The last edge wraps back to vertex 0, so it is adjacent to edge 0.
          if (i == 0 && j == n - 1) continue
          val b0 = points[j]
          val b1 = points[(j + 1) % n]
          if (Intersections.segmentCrossing(a0, a1, b0, b1) != null) return true
        }
      }
      return false
    }
  }
}
