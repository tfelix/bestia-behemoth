package net.bestia.worldgen.civ

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.fields.DoubleIntHeap
import net.bestia.worldgen.vector.Intersections
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Quantize
import net.bestia.worldgen.vector.Ring
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What a town is being laid out on: where it is, how far it reaches, and what the ground will allow.
 *
 * Passed in rather than queried, because the layout runs entirely in local geometry and the two things it
 * needs from the world - "how high is the ground here" and "may I build here" - are the only places the
 * world gets to have an opinion. Keeping them as functions is also what makes the layout testable without
 * a generated world behind it.
 */
internal class TownFrame(
  val centre: Vec2d,
  /** Radius of the built-up area in metres, from present population rather than from the tier. */
  val builtRadius: Double,
  /**
   * The edge of the built-up area, as a shape rather than as a radius.
   *
   * The town used to *be* [builtRadius] - streets stopped at it, the grid was clipped to it by half-chord, land
   * value was a fraction of it and the wall was a circle drawn at it - and a settlement generated from one
   * number is shaped like that number's level set, which is a disc. This is the same argument
   * [net.bestia.worldgen.vector.Ring.warpedCircle]'s own KDoc makes about ponds reading as craters.
   *
   * [builtRadius] survives as the *scale* the boundary is built at, and as the bound this ring is guaranteed to
   * lie inside - which is what keeps every lot on ground the settlement's grading disc actually levelled. See
   * [TownBoundary].
   */
  val boundary: Ring,
  /** Approximate ground elevation, already terraced the way the settlement's grading will terrace it. */
  val groundAt: (Vec2d) -> Double,
  /** False over water, over ground too steep to build on, or outside the world. */
  val buildable: (Vec2d) -> Boolean,
  /** Unit directions from which roads arrive. Empty for a place no road reaches. */
  val approaches: List<Vec2d>
) {

  /** Whether a position is inside the built-up area. Replaces `distanceTo(centre) <= builtRadius`. */
  fun encloses(at: Vec2d): Boolean = boundary.contains(at)
}

/**
 * The shape of a town's built edge: a warped circle, stretched along whatever the town is strung out along.
 *
 * ### Why a stretch and not more noise
 *
 * Noise alone gives a round town with a wobbly edge, because fbm around a circle has no preferred direction -
 * every lobe is as likely to point one way as another, so the *outline* varies and the *aspect* does not. Real
 * towns are elongated, and they are elongated along something: the river they cross, or the road they grew
 * beside. A single affine stretch says that in one multiplication, and says it about the town rather than about
 * its edge.
 *
 * An invertible affine map is a homeomorphism of the plane, so it takes a simple polygon to a simple polygon -
 * the stretch cannot make [Ring]'s self-intersection check start rejecting boundaries, whatever the aspect.
 *
 * ### Area, and the one place this costs something
 *
 * The stretch is `(sqrt(aspect), 1/sqrt(aspect))`, which **preserves area** - so a town that is elongated holds
 * as many plots as the round one it replaced, rather than silently getting more or fewer. That matters because
 * `TownParams.peoplePerHectare` is measured, not assumed, and an area change would quietly invalidate it.
 *
 * The exception is a settlement whose long axis would then reach past [TownFrame.builtRadius], which is the
 * bound `TownStage.builtRadiusFor` reserved room inside the graded footprint for. There the ring is scaled down
 * uniformly until it fits, and *that* costs area. It binds on the largest settlements only - a city's radius is
 * capped by its graded footprint long before its population stops asking for more - and it is the honest
 * trade, because the alternative is buildings standing on a hillside the town never levelled.
 */
internal object TownBoundary {

  fun of(
    centre: Vec2d,
    builtRadius: Double,
    /** What the town is strung out along. Unit length; any direction will do, including a rolled one. */
    axis: Vec2d,
    aspect: Double,
    seed: Long,
    params: StreetParams
  ): Ring {
    val warped = Ring.warpedCircle(
      centre = Vec2d.ZERO,
      radius = builtRadius,
      seed = seed,
      vertexCount = params.boundaryVertices,
      roughness = params.boundaryRoughness,
      lobes = params.boundaryLobes
    )

    val along = axis.normalized().let { if (it.lengthSquared < 0.5) Vec2d(1.0, 0.0) else it }
    val across = along.perpendicular()
    val stretch = sqrt(aspect)

    // Built about the origin and translated at the end, so the stretch is about the town's own centre rather
    // than about the world's, which at a hundred kilometres out would be a shear the size of the map.
    val stretched = warped.vertices.map { v ->
      (along * ((v dot along) * stretch)) + (across * ((v dot across) / stretch))
    }

    // Uniform, so the shape is kept and only its size gives way. Measured from the centre because that is what
    // `builtRadius` is a radius from.
    val reach = stretched.maxOf { it.length }
    val fit = if (reach > builtRadius) builtRadius / reach else 1.0

    return Ring(stretched.map { centre + it * fit })
  }

  /**
   * How elongated this town is, rolled per settlement.
   *
   * Jittered rather than fixed because a world where every town has the same aspect reads as a template, which
   * is the failure the whole change is against. The floor is above one so that the axis always means something:
   * an aspect of exactly one is a round town again, and then the direction it was given is unobservable.
   */
  fun aspectOf(roll: (Long, Long) -> Double, params: StreetParams): Double =
    params.boundaryAspect * (1.0 + (roll(0L, ASPECT_SALT) - 0.5) * 2.0 * params.boundaryAspectJitter)

  private const val ASPECT_SALT = 0x38L
}

/**
 * One street segment before the graph is planarised.
 *
 * [rank] is the street's importance, zero being the most important. It survives planarisation and decides
 * the carriageway width, the surface, and how much land value a lot fronting it has - which is what makes
 * shops appear on the high street rather than evenly.
 */
internal class StreetSegment(val a: Vec2d, val b: Vec2d, val rank: Int)

/**
 * A planar street graph, and the two things a town wants out of one: chains to stamp, and faces to build in.
 *
 * Planar is the load-bearing word. Two streets that cross must share a node, or the face traversal walks
 * straight through the crossing and returns one enormous face instead of the four small ones that are the
 * blocks - so every crossing is found and split before this is constructed, never after.
 */
internal class StreetGraph(
  val nodes: List<Vec2d>,
  val edges: List<Edge>
) {

  class Edge(val a: Int, val b: Int, val rank: Int)

  /** Edge indices incident to each node, sorted counter-clockwise by direction. */
  private val incident: List<IntArray> = run {
    val buckets = Array(nodes.size) { ArrayList<Int>() }
    edges.forEachIndexed { i, edge ->
      buckets[edge.a].add(i)
      buckets[edge.b].add(i)
    }
    buckets.mapIndexed { node, list ->
      list.sortedBy { angleOf(node, it) }.toIntArray()
    }
  }

  private fun angleOf(from: Int, edge: Int): Double {
    val other = if (edges[edge].a == from) edges[edge].b else edges[edge].a
    val d = nodes[other] - nodes[from]
    return atan2(d.y, d.x)
  }

  fun rankAt(node: Int): Int = incident[node].minOfOrNull { edges[it].rank } ?: Int.MAX_VALUE

  fun degreeOf(node: Int): Int = incident[node].size

  /**
   * Every node one edge away, with the length of the edge that reaches it. What [StreetDistance] walks.
   *
   * The graph's adjacency was already built - [incident] exists so `chains()` can follow a street through a
   * junction - and it was private, so a shortest-path walk over the same structure would otherwise have had to
   * rebuild it. Returning positions' distance rather than a hop count matters: a town's streets differ in length
   * by an order of magnitude between a market frontage and a road out, and hop counts would make a long straight
   * approach road look closer to the market than the lane behind it.
   */
  fun neighboursOf(node: Int): List<Pair<Int, Double>> = incident[node].map {
    val other = if (edges[it].a == node) edges[it].b else edges[it].a
    other to nodes[node].distanceTo(nodes[other])
  }

  /**
   * Whether any two edges cross somewhere other than at a node they share - i.e. whether this graph is *not*
   * planar as constructed.
   *
   * The property [StreetPlanner.planarise] iterates until this is false, and the one `LotPlanner` relies on
   * when it rejects a plot that reaches across a street: a crossing with no node at it is a street the plot
   * tests cannot see. `O(edges²)` with a bounding-box reject inside `segmentCrossing`, on a few hundred edges,
   * and it runs once per round rather than per query.
   */
  fun hasCrossing(): Boolean {
    for (i in edges.indices) {
      for (j in i + 1 until edges.size) {
        val a = edges[i]
        val b = edges[j]
        // Edges meeting at a shared node are the normal case and are not crossings.
        if (a.a == b.a || a.a == b.b || a.b == b.a || a.b == b.b) continue
        if (Intersections.segmentCrossing(nodes[a.a], nodes[a.b], nodes[b.a], nodes[b.b]) != null) return true
      }
    }
    return false
  }

  /**
   * Every street segment whose bounding box reaches [around], as `(a, b)` world positions.
   *
   * What [LotPlanner] tests a candidate plot against, so that a plot laid from one street cannot reach across
   * a parallel street behind it.
   */
  fun segmentsNear(around: Vec2d, radius: Double): List<Pair<Vec2d, Vec2d>> {
    val out = ArrayList<Pair<Vec2d, Vec2d>>()
    for (edge in edges) {
      val a = nodes[edge.a]
      val b = nodes[edge.b]
      // Cheap reject on the segment's own extent before the point-to-segment distance.
      if (min(a.x, b.x) - radius > around.x || max(a.x, b.x) + radius < around.x) continue
      if (min(a.y, b.y) - radius > around.y || max(a.y, b.y) + radius < around.y) continue
      out.add(a to b)
    }
    return out
  }

  /**
   * Streets as polylines, one per maximal chain of same-rank edges.
   *
   * Chained rather than emitted per edge because a street is stamped as a [Polyline] feature, and a hundred
   * two-vertex features cost a hundred spatial-index entries and a hundred corridor queries per chunk where
   * one chain of a hundred vertices costs one. The chain also gives the station spline something to smooth,
   * which is what stops a grown street from reading as a chain of straights.
   */
  fun chains(): List<Pair<Polyline, Int>> {
    val used = BooleanArray(edges.size)
    val out = ArrayList<Pair<Polyline, Int>>()

    // Longest-first by rank so a high street claims its whole run before a lane takes a bite out of it.
    val order = edges.indices.sortedWith(compareBy({ edges[it].rank }, { it }))

    for (seed in order) {
      if (used[seed]) continue

      val rank = edges[seed].rank
      used[seed] = true
      val chain = ArrayDeque<Int>()
      chain.addLast(edges[seed].a)
      chain.addLast(edges[seed].b)

      extend(chain, rank, used, forwards = true)
      extend(chain, rank, used, forwards = false)

      val points = chain.map { nodes[it] }
      runCatching { Polyline(points) }.getOrNull()?.let { out.add(it to rank) }
    }

    return out
  }

  /**
   * Walks a chain onward while the next step is unambiguous.
   *
   * "Unambiguous" means exactly one unused edge of the same rank leaves the end and it is closest to
   * straight ahead. Following a turn at a crossroads would produce a street that changes name halfway
   * along, which is what makes a stamped centerline swing across a junction.
   */
  private fun extend(chain: ArrayDeque<Int>, rank: Int, used: BooleanArray, forwards: Boolean) {
    while (true) {
      val end = if (forwards) chain.last() else chain.first()
      val previous = if (forwards) chain[chain.size - 2] else chain[1]
      val heading = (nodes[end] - nodes[previous]).normalized()

      var best = -1
      var bestAlignment = STRAIGHT_ENOUGH

      for (candidate in incident[end]) {
        if (used[candidate] || edges[candidate].rank != rank) continue
        val other = if (edges[candidate].a == end) edges[candidate].b else edges[candidate].a
        if (other == previous) continue

        val alignment = heading dot (nodes[other] - nodes[end]).normalized()
        if (alignment > bestAlignment) {
          bestAlignment = alignment
          best = candidate
        }
      }

      if (best < 0) return

      used[best] = true
      val other = if (edges[best].a == end) edges[best].b else edges[best].a
      if (forwards) chain.addLast(other) else chain.addFirst(other)
    }
  }

  private companion object {
    /**
     * How straight a continuation has to be to count as the same street, as a dot product.
     *
     * Zero would let a chain turn a right angle. This allows about sixty degrees, which keeps a grown
     * street bending through a town without letting it turn a corner.
     */
    const val STRAIGHT_ENOUGH = 0.5
  }
}

/**
 * How far anywhere in a town is from its market **along the streets**, normalised to `[0,1]`.
 *
 * ### Why not the distance to the centre
 *
 * Land value used to be `1 - distanceToCentre / builtRadius`, and that is a statement about a disc rather than
 * about a town. It says a plot two hundred metres from the market up a dead-end lane with no way through is
 * worth exactly as much as one two hundred metres away on the high street - and worse, it is the reason the
 * *contents* of a settlement were laid out in bands however un-circular its streets were. A radial land value
 * puts the temples in a ring and the farms in an annulus no matter what shape the town is.
 *
 * Walking distance is what land value actually tracked historically, and it is what a player experiences: the
 * question is how long it takes to get to the market, not how far it is as the crow flies.
 *
 * ### Unreachable falls back to the crow, and the reason is not theoretical
 *
 * A node the market cannot reach used to normalise to 1 - maximally peripheral - on the argument that land across
 * an uncrossable river genuinely is the cheapest land in town. That argument is fine and the behaviour was not:
 * planarisation and the buildable filter leave *fragments*, short runs of street whose connection to the middle
 * was dropped for a metre of water or a steep segment, and those are ordinary town streets rather than the far
 * bank. Scoring them at 1 put them past `Zoning`'s farm threshold, and a town came back **63 per cent farmsteads**
 * with fifty-one houses in it.
 *
 * So an unreachable node falls back to straight-line distance from the market, normalised against the same span.
 * A fragment near the middle then scores near the middle, a fragment at the edge scores at the edge, and neither
 * gets a value the rest of the scale cannot produce. It is a worse estimate than a walk and a far better one than
 * a cliff.
 *
 * Distances are per node, and a query interpolates by adding the straight line from the nearest node. A lot is
 * within half a segment of a node by construction, so the error is metres on a scale of hundreds, and the
 * consumer is a normalised score that decides an ordering.
 */
internal class StreetDistance(private val graph: StreetGraph, private val origin: Vec2d) {

  private val distance = DoubleArray(graph.nodes.size) { Double.MAX_VALUE }

  /** Longest finite distance, so the normalisation is against the town's own extent rather than a guess. */
  private val span: Double

  /** Node buckets, so a per-lot query is not a scan over every junction in the town. */
  private val buckets: HashMap<Long, MutableList<Int>>
  private val bucketSize: Double

  init {
    val start = nearestByScan(origin)
    if (start >= 0) {
      distance[start] = 0.0
      val open = DoubleIntHeap(graph.nodes.size.coerceAtLeast(16))
      val settled = BooleanArray(graph.nodes.size)
      open.push(0.0, start)

      while (!open.isEmpty) {
        val node = open.pop()
        if (settled[node]) continue
        settled[node] = true

        for ((other, step) in graph.neighboursOf(node)) {
          val candidate = distance[node] + step
          if (candidate >= distance[other]) continue
          distance[other] = candidate
          open.push(candidate, other)
        }
      }
    }

    val furthest = distance.filter { it != Double.MAX_VALUE }.maxOrNull() ?: 0.0
    // Floored so that a town whose whole network is one junction cannot divide by zero, and so that a hamlet
    // with two streets does not report its second street as maximally peripheral.
    span = max(furthest, MIN_SPAN)

    bucketSize = max(span / BUCKET_DIVISIONS, MIN_BUCKET)
    buckets = HashMap()
    for (i in graph.nodes.indices) {
      buckets.getOrPut(keyOf(graph.nodes[i])) { ArrayList(4) }.add(i)
    }
  }

  /** Normalised walking distance from the market: 0 at it, 1 at the far end of the network. */
  fun at(point: Vec2d): Double {
    val node = nearest(point) ?: return asTheCrowFlies(point)
    val reached = distance[node]
    if (reached == Double.MAX_VALUE) return asTheCrowFlies(point)
    return ((reached + point.distanceTo(graph.nodes[node])) / span).coerceIn(0.0, 1.0)
  }

  /** The fallback for a point the market cannot walk to. See this class's own note on why it is not just 1. */
  private fun asTheCrowFlies(point: Vec2d): Double = (point.distanceTo(origin) / span).coerceIn(0.0, 1.0)

  /** Nearest node, searching the query bucket and its ring of neighbours before widening. */
  private fun nearest(point: Vec2d): Int? {
    val cellX = Math.floor(point.x / bucketSize).toLong()
    val cellY = Math.floor(point.y / bucketSize).toLong()

    var best = -1
    var bestSq = Double.MAX_VALUE
    for (dy in -1..1) {
      for (dx in -1..1) {
        val bucket = buckets[(cellX + dx) * PRIME xor (cellY + dy)] ?: continue
        for (i in bucket) {
          val d = graph.nodes[i].distanceSquaredTo(point)
          if (d < bestSq) {
            bestSq = d
            best = i
          }
        }
      }
    }

    // A point more than a bucket from any junction - which happens on a sparse hamlet - falls back to the scan
    // rather than to "peripheral", because getting this wrong would silently zone a whole small settlement.
    return if (best >= 0) best else nearestByScan(point).takeIf { it >= 0 }
  }

  private fun nearestByScan(point: Vec2d): Int {
    var best = -1
    var bestSq = Double.MAX_VALUE
    for (i in graph.nodes.indices) {
      val d = graph.nodes[i].distanceSquaredTo(point)
      if (d < bestSq) {
        bestSq = d
        best = i
      }
    }
    return best
  }

  private fun keyOf(p: Vec2d): Long =
    Math.floor(p.x / bucketSize).toLong() * PRIME xor Math.floor(p.y / bucketSize).toLong()

  private companion object {
    /** Metres below which a town's street network is treated as having no extent worth normalising against. */
    const val MIN_SPAN = 40.0

    /** Buckets across the town's extent, and a floor so a tiny settlement gets one bucket rather than millions. */
    const val BUCKET_DIVISIONS = 12.0
    const val MIN_BUCKET = 25.0

    const val PRIME = 0x9E3779B1L
  }
}

/**
 * Tuning for the street layout.
 *
 * Public, and it was not. It had a public mirror in `TownParams` - a `StreetParamsPublic` that copied seven of
 * these nine fields and converted itself back - and the mirror had drifted: it declared `minRadials = 3` where
 * this declared 4, and since every call went through the conversion, **4 and the reasoning written beside it
 * had never once run.** `rings` and `ringVertices` were not on the mirror at all, so neither was reachable.
 *
 * The mirror existed to keep the layout internals out of the public API, which was a reasonable aim and cost
 * more than it bought: two field lists that must agree, no compiler check that they do, and a silent winner
 * when they disagree. This class is the one copy now, at the value that has actually been in effect (3).
 * Whether 3 or 4 is the better town is a measurement, not a refactor - it wants its own change and a look at
 * `./gradlew :worldgen:town`.
 *
 * `rings` is a `List<Double>` rather than a `DoubleArray` for a reason worth knowing: an array's `hashCode`
 * and `toString` are identity-based, so a params class holding one cannot be fingerprinted - see
 * [ParamsDigest]. It is read once per town, so boxing is irrelevant, and the list makes the constraint
 * structural instead of a comment nobody reads.
 */
data class StreetParams(
  /** Metres per grown segment. Also the grid's block size. */
  val segmentLength: Double = 34.0,
  /** Radians a grown segment may wander from its parent's heading. */
  val angleJitter: Double = 0.28,
  /** Chance a grown segment throws a side street. */
  val branchChance: Double = 0.28,
  /** Metres within which a growing street snaps to an existing node instead of passing it. */
  val snapRadius: Double = 11.0,
  /** Fewest radial streets, however few roads arrive. A town with one road still has a crossroads. */
  val minRadials: Int = 3,
  val maxRadials: Int = 7,
  /**
   * Cross streets, as fractions of the built radius.
   *
   * These were three *closed rings*, and closed rings at surveyed radii are most of why every town in the world
   * read as a wheel: a ring is far the longest chain in the network, so most plots front onto one, and three of
   * them concentric put every house in the town on one of three circles. They are now arcs - see
   * [StreetPlanner.crossSegments] - but the radii keep their meaning and their reason. Three rather than two,
   * spread inwards: with cross streets only at 0.42 and 0.82 the middle of a town came out nearly empty, and a
   * town is densest at its centre.
   */
  val rings: List<Double> = listOf(0.28, 0.55, 0.82),
  /** Vertices per full turn of a cross street. Enough that an arc reads as a curve rather than a polygon. */
  val ringVertices: Int = 20,
  /**
   * Share of a full turn one cross street covers, before jitter.
   *
   * The number that stops a cross street being a ring. Two thirds is enough to tie several radials together -
   * which is what the cycles the network needs come from - and short enough that the eye never closes it into a
   * circle.
   */
  val arcSpan: Double = 0.66,
  /** Fraction of [arcSpan] the span and its starting bearing are rolled either side of. */
  val arcJitter: Double = 0.3,
  /** Vertices around the town's own edge. Capped by `Ring.MAX_VERTICES`. */
  val boundaryVertices: Int = 28,
  /**
   * Fraction of the built radius the town's edge wanders, in `[0,1)`.
   *
   * Lower than `Ring.warpedCircle`'s own default of 0.28, because that default is tuned for a *landform* - a
   * lake shore wants bays - and a built edge that deep in and out reads as a coastline rather than as the point
   * where the houses stop.
   */
  val boundaryRoughness: Double = 0.10,
  /** How many broad lobes the edge has. Few and wide, so the shape is a shape and not a fringe. */
  val boundaryLobes: Double = 2.4,
  /**
   * How much longer than wide a town is, before jitter. See [TownBoundary].
   *
   * Modest on purpose, and the reason is [boundaryReachFactor]: a shape's bounding circle is bigger than that of
   * a disc of the same area, so every point of elongation has to be paid for in graded ground. One and a half is
   * where a town plainly is not a circle and the bill is still under a half.
   */
  val boundaryAspect: Double = 1.45,
  /** Fraction of [boundaryAspect] the aspect is rolled either side of, per settlement. */
  val boundaryAspectJitter: Double = 0.21,
  /** Deepest a grown street may go, in segments. */
  val maxDepth: Int = 9
) : Params {

  init {
    require(segmentLength > 0.0) { "segmentLength must be positive, was $segmentLength" }
    require(angleJitter >= 0.0) { "angleJitter must not be negative, was $angleJitter" }
    require(branchChance in 0.0..1.0) { "branchChance must be in [0,1], was $branchChance" }
    require(snapRadius >= 0.0) { "snapRadius must not be negative, was $snapRadius" }
    require(minRadials in 1..maxRadials) {
      "minRadials $minRadials must be in [1, maxRadials $maxRadials]; a town with no radial has no streets"
    }
    // Fractions of the built radius, so a ring at or beyond 1.0 is outside the town it is meant to encircle.
    require(rings.all { it > 0.0 && it < 1.0 }) {
      "every ring must be a fraction of the built radius in (0,1), was $rings"
    }
    // Three vertices is the fewest that closes; below it a ring is a line and `planarise` welds it to nothing.
    require(ringVertices >= 3) { "ringVertices must be at least 3, was $ringVertices" }
    // At a full turn a cross street is a closed ring again, which is the thing this replaced.
    require(arcSpan > 0.0 && arcSpan < 1.0) { "arcSpan must be a share of a turn in (0,1), was $arcSpan" }
    require(arcJitter >= 0.0 && arcJitter <= 1.0) { "arcJitter must be in [0,1], was $arcJitter" }
    require(boundaryVertices in 3..Ring.MAX_VERTICES) {
      "boundaryVertices must be in [3, ${Ring.MAX_VERTICES}], was $boundaryVertices"
    }
    require(boundaryRoughness >= 0.0 && boundaryRoughness < 1.0) {
      "boundaryRoughness must be in [0,1), was $boundaryRoughness"
    }
    require(boundaryLobes > 0.0) { "boundaryLobes must be positive, was $boundaryLobes" }
    // Below one the "long" axis is the short one, which is the same shape rolled a quarter turn and only
    // confuses what the axis means. Exactly one is a round town, which is what this exists to stop.
    require(boundaryAspect > 1.0) { "boundaryAspect must be greater than 1, was $boundaryAspect" }
    // Jitter that could reach an aspect of one would occasionally hand back the disc.
    require(boundaryAspectJitter >= 0.0 && boundaryAspect * (1.0 - boundaryAspectJitter) > 1.0) {
      "boundaryAspectJitter $boundaryAspectJitter must keep aspect $boundaryAspect above 1 at its low end"
    }
    require(maxDepth >= 1) { "maxDepth must be at least 1, was $maxDepth" }
  }

  /**
   * How much wider than a disc of the same area this town's bounding circle has to be.
   *
   * **The number that pays for the shape.** A circle is the shape with the smallest bounding circle for its area;
   * anything else needs a bigger one. So a town that is elongated by `aspect` and wanders by `roughness`, and
   * that holds as many plots as the disc it replaced, reaches `sqrt(aspect) * (1 + roughness)` further from its
   * centre than that disc did. `TownStage.builtRadiusFor` reserves room for exactly this, and
   * `SettlementTier.footprintRadius` was raised by it, so that de-circularising a town changed its *shape*
   * without also quietly making it smaller. Getting this wrong does not look like a bug: it looks like a world of
   * towns that all report wanting forty per cent more buildings than their streets have room for.
   *
   * Taken at the *top* of the aspect jitter, because it is a reservation and the roll happens later. A body `val`
   * rather than a constructor property on purpose - it is derived from three tunables that are already folded
   * into the digest, and `ParamsFields` would rightly flag a fourth that is not independent.
   */
  val boundaryReachFactor: Double
    get() = sqrt(boundaryAspect * (1.0 + boundaryAspectJitter)) * (1.0 + boundaryRoughness)

  override fun digest() = ParamsDigest()
    .put("segmentLength", segmentLength)
    .put("angleJitter", angleJitter)
    .put("branchChance", branchChance)
    .put("snapRadius", snapRadius)
    .put("minRadials", minRadials)
    .put("maxRadials", maxRadials)
    .put("rings", rings)
    .put("ringVertices", ringVertices)
    .put("arcSpan", arcSpan)
    .put("arcJitter", arcJitter)
    .put("boundaryVertices", boundaryVertices)
    .put("boundaryRoughness", boundaryRoughness)
    .put("boundaryLobes", boundaryLobes)
    .put("boundaryAspect", boundaryAspect)
    .put("boundaryAspectJitter", boundaryAspectJitter)
    .put("maxDepth", maxDepth)
}

/**
 * Lays out a town's streets: agent growth for an organic town, a clipped grid for a planned one.
 *
 * Both end in the same place - a set of segments handed to [planarise] - and that is the point. The two
 * algorithms differ in what they produce, not in what happens to it, so the block extraction, the lots and
 * the buildings are written once.
 */
internal object StreetPlanner {

  /**
   * @param extra streets some other producer decided - the edges of a patched core, when a town has one.
   *   Planarised together with the grown ones rather than beside them, because the whole town has to be **one**
   *   planar graph: `LotPlanner` rejects a plot that reaches across a street by testing the graph, so a street the
   *   graph does not hold is a street plots grow through. They also go through the same [passable] filter, so a
   *   patch edge over water is dropped exactly as a grown segment would be.
   */
  fun plan(
    frame: TownFrame,
    layout: TownLayout,
    /** Keyed roll, `(salt...) -> [0,1)`. Never a stream: see [net.bestia.worldgen.history.HistorySim]. */
    roll: (Long, Long) -> Double,
    params: StreetParams = StreetParams(),
    extra: List<StreetSegment> = emptyList()
  ): StreetGraph {
    val raw = when (layout) {
      TownLayout.ORGANIC -> organic(frame, roll, params)
      TownLayout.GRID -> grid(frame, roll, params)
    }

    return planarise((raw + extra).filter { inside(frame, it) && passable(frame, it) })
  }

  /**
   * Whether both ends of a segment lie inside the town.
   *
   * The grown layouts check this as they go - a growth step that would leave the boundary is not taken - but
   * [plan]'s `extra` comes from somewhere else, and a patch's edges are bounded by its Voronoi neighbours rather
   * than by the town's outline. So a core street could run outside the town, and `LotPlanner` would then front plots
   * onto it from outside: the 320-cell sweep caught a building 695 m from a settlement whose footprint is 610.
   *
   * Filtering the whole set rather than only `extra`, because a bound that every producer must remember to apply
   * is a bound that some producer will forget - which is exactly what happened.
   */
  private fun inside(frame: TownFrame, segment: StreetSegment): Boolean =
    frame.encloses(segment.a) && frame.encloses(segment.b)

  /**
   * A segment is kept only if it is buildable along its whole length, sampled at five points.
   *
   * Five rather than two, and that is not belt and braces: the thing being avoided is a river channel a
   * dozen metres wide crossing a segment thirty metres long, and testing only the ends lets a street run
   * straight through it - which then dams the river, because a street is `REPLACE`-blended and stamped at a
   * higher priority than the channel.
   */
  private fun passable(frame: TownFrame, segment: StreetSegment): Boolean =
    (0..4).all { frame.buildable(segment.a.lerp(segment.b, it / 4.0)) }

  // --- Organic --------------------------------------------------------------------------------------

  /**
   * Radials out of the market square, side streets branching off them, and a few cross streets.
   *
   * The cross streets are not decoration. A pure branching growth is a *tree*, and a tree has no cycles, so
   * nothing closes a block; snapping produces some cycles opportunistically and cannot be relied on to. That
   * failure is worth naming because it does not look like a missing street - it looks like a town with streets
   * and nothing on them. See [crossSegments] for why they are arcs rather than the rings they used to be.
   */
  private fun organic(
    frame: TownFrame,
    roll: (Long, Long) -> Double,
    params: StreetParams
  ): List<StreetSegment> {
    val out = ArrayList<StreetSegment>()
    val nodes = ArrayList<Vec2d>()
    nodes.add(frame.centre)

    val directions = radialDirections(frame, roll, params)

    // Radials first, rank 0: these are the streets the town is organised around.
    val frontier = ArrayDeque<Growth>()
    for ((i, direction) in directions.withIndex()) {
      frontier.addLast(Growth(frame.centre, direction, rank = 0, depth = 0, salt = i.toLong()))
    }

    // Far enough for a radial to reach the outermost ring, whatever the town's size. A fixed depth left the
    // rings of a large town unconnected to its centre, which is visible from above as a set of terraces with
    // no way between them.
    val maxDepth = max(params.maxDepth, (frame.builtRadius / params.segmentLength).toInt() + 2)

    var stepCounter = 0L
    while (frontier.isNotEmpty()) {
      val growth = frontier.removeFirst()
      if (growth.depth >= maxDepth) continue

      val salt = growth.salt * 31 + stepCounter++
      val wander = (roll(salt, WANDER_SALT) - 0.5) * 2.0 * params.angleJitter
      val heading = rotate(growth.heading, wander * (1 + growth.rank))
      val step = params.segmentLength * (if (growth.rank == 0) 1.0 else 0.8)
      var end = growth.from + heading * step

      if (!frame.encloses(end)) continue

      // Snap to a node already there rather than run past it a metre away, which is what turns a grown
      // network into a connected one - and every snap is a new cycle, hence a new block.
      nodes.minByOrNull { it.distanceTo(end) }?.let { nearest ->
        if (nearest.distanceTo(end) < params.snapRadius && nearest.distanceTo(growth.from) > 1.0) {
          end = nearest
        }
      }
      if (end.distanceTo(growth.from) < 1.0) continue

      out.add(StreetSegment(growth.from, end, growth.rank))
      if (nodes.none { it.distanceTo(end) < 0.5 }) nodes.add(end)

      frontier.addLast(Growth(end, heading, growth.rank, growth.depth + 1, salt))

      if (roll(salt, BRANCH_SALT) < params.branchChance && growth.rank < 2) {
        val side = if (roll(salt, SIDE_SALT) < 0.5) 1.0 else -1.0
        val turned = rotate(heading, side * (PI / 2 + (roll(salt, TURN_SALT) - 0.5) * 0.5))
        frontier.addLast(Growth(end, turned, growth.rank + 1, growth.depth + 2, salt))
      }
    }

    out.addAll(crossSegments(frame, params, rank = 1, roll = roll))
    return out
  }

  private class Growth(
    val from: Vec2d,
    val heading: Vec2d,
    val rank: Int,
    val depth: Int,
    val salt: Long
  )

  /**
   * Which way the main streets run: towards the roads that arrive, plus enough invented ones to make a
   * crossroads.
   *
   * Aligning on the approaches is what ties a town to its region: the high street points at the next town,
   * so the road, the gate and the market are on one line, which is how it worked and how it reads.
   */
  private fun radialDirections(
    frame: TownFrame,
    roll: (Long, Long) -> Double,
    params: StreetParams
  ): List<Vec2d> {
    val out = ArrayList<Vec2d>(frame.approaches)

    var attempt = 0L
    while (out.size < params.minRadials && attempt < 64) {
      val angle = roll(attempt, RADIAL_SALT) * 2.0 * PI
      val candidate = Vec2d(cos(angle), sin(angle))
      // Not too close to one already chosen, or two "radials" leave as one wide street.
      if (out.none { it dot candidate > 0.94 }) out.add(candidate)
      attempt++
    }

    return out.take(params.maxRadials)
  }

  /**
   * Cross streets: arcs at fractions of the built radius, wandering rather than circular, and **open**.
   *
   * ### Two separate reasons this is not a ring
   *
   * The wander was here first and its reason still holds: an exact circle produces a town that reads as a
   * diagram of a town from above. The perturbation is a sum of two harmonics rather than per-vertex noise
   * because a street has to stay smooth - independent jitter per vertex gives corners every forty metres, which
   * is a worse artefact than the circle it was fixing.
   *
   * But a *wandering* circle is still a circle, and that was the deeper problem. A cross street is far the
   * longest chain in the network, so most of a town's plots front onto one; three closed ones concentric
   * therefore put nearly every house in the town on one of three circles, and no amount of edge wander hides
   * three concentric terraces. So each one now covers only [StreetParams.arcSpan] of a turn, from a rolled
   * bearing, at a rolled radius.
   *
   * Cycles survive this, which is the property the network genuinely needs - a pure branching growth is a tree,
   * and the ring streets were originally introduced to guarantee cycles at all. An arc spanning two thirds of a
   * turn crosses several radials, and every crossing closes a loop between the arc and the two radials it joins.
   * The arcs are also no longer forced to the same phase, so those loops differ in size and shape, which is
   * what a block should do.
   *
   * The last vertex is deliberately not joined back to the first. That single omitted segment is the whole
   * difference between a cross street and a ring road.
   */
  private fun crossSegments(
    frame: TownFrame,
    params: StreetParams,
    rank: Int,
    roll: (Long, Long) -> Double
  ): List<StreetSegment> {
    val out = ArrayList<StreetSegment>()

    for ((index, fraction) in params.rings.withIndex()) {
      val salt = index.toLong()
      val radius = frame.builtRadius * fraction
      // An arc shorter than a block is the market square, not a street.
      if (radius < params.segmentLength) continue

      val phaseA = roll(salt, RING_PHASE_SALT) * 2.0 * PI
      val phaseB = roll(salt, RING_PHASE_SALT + 1) * 2.0 * PI
      val wander = { turn: Double ->
        val angle = turn * 2.0 * PI
        radius * (1.0 + RING_WANDER * (sin(angle * 2.0 + phaseA) + 0.6 * sin(angle * 3.0 + phaseB)) / 1.6)
      }

      // Rolled independently per arc, so two cross streets are not two arcs of one broken ring.
      val span = (params.arcSpan * (1.0 + (roll(salt, ARC_SPAN_SALT) - 0.5) * 2.0 * params.arcJitter))
        .coerceIn(MIN_ARC_SPAN, MAX_ARC_SPAN)
      val from = roll(salt, ARC_START_SALT)

      // Kept at the ring's own vertex density rather than the arc's, so a short arc is not a coarse polygon.
      val steps = max(2, (params.ringVertices * span).toInt())
      for (i in 0 until steps) {
        val ta = from + span * i / steps
        val tb = from + span * (i + 1) / steps
        val a = onCircle(frame.centre, wander(ta), ta)
        val b = onCircle(frame.centre, wander(tb), tb)
        // Clipped to the town's own edge rather than to the radius the arc was drawn at: the boundary is
        // stretched, so an arc at 0.82 of the radius leaves the town across its short axis and stays well
        // inside it along the long one.
        if (!frame.encloses(a) || !frame.encloses(b)) continue
        out.add(StreetSegment(a, b, rank))
      }
    }

    return out
  }

  private fun onCircle(centre: Vec2d, radius: Double, turn: Double): Vec2d {
    val angle = turn * 2.0 * PI
    return Vec2d(centre.x + cos(angle) * radius, centre.y + sin(angle) * radius)
  }

  // --- Grid -----------------------------------------------------------------------------------------

  /**
   * A rotated grid clipped to the built area, aligned to the dominant approach road.
   *
   * Aligned rather than axis-aligned because an axis-aligned grid in a world whose roads run at every angle
   * looks like the coordinate system showing through. Rotating it to the road that matters most makes the
   * main avenue the continuation of the highway, which is what a chartered town did.
   *
   * ### The clip used to be a circle, and that was most of the problem
   *
   * A planned town is the one case where the *streets* are surveyed and straight, so the only thing that gives
   * it an outline at all is where they stop - and they stopped at the half-chord of a circle, which made a
   * chartered town a disc with a grid stamped in it. Clipping each line against [TownFrame.boundary] instead
   * costs the same arithmetic and gives the grid the town's own edge: long avenues down the axis, short cross
   * streets, ragged ends. Which is what a planned town on real ground looks like.
   */
  private fun grid(
    frame: TownFrame,
    roll: (Long, Long) -> Double,
    params: StreetParams
  ): List<StreetSegment> {
    val axis = frame.approaches.firstOrNull()
      ?: (roll(0L, GRID_SALT) * PI).let { Vec2d(cos(it), sin(it)) }
    val across = axis.perpendicular()

    val spacing = params.segmentLength * GRID_BLOCK_MULTIPLE
    // The boundary's own reach, not `builtRadius`. The two are equal only for a town whose long axis was scaled
    // back to fit; an elongated town's edge stops short of the radius everywhere except along that axis, and
    // counting lines from the radius would lay most of them entirely outside the town for `addLine` to discard.
    val reach = frame.boundary.vertices.maxOf { it.distanceTo(frame.centre) }
    val lines = (reach / spacing).toInt()

    val out = ArrayList<StreetSegment>()

    for (i in -lines..lines) {
      val offset = i * spacing
      // Avenues along the axis, streets across it. Rank 0 for the centre line of each family, so the two
      // main streets meeting at the market are the important ones.
      val rankAlong = if (i == 0) 0 else 1
      addLine(out, frame, frame.centre + across * offset, axis, reach, rankAlong, spacing)
      addLine(out, frame, frame.centre + axis * offset, across, reach, if (i == 0) 0 else 2, spacing)
    }

    return out
  }

  /**
   * One grid line, in segments, keeping only the runs of it that lie inside the town.
   *
   * Per segment rather than by solving for the entry and exit points, because a warped boundary is not convex -
   * a line may leave the town and come back, and a two-point clip would bridge the gap with a street across
   * whatever the boundary was avoiding.
   */
  private fun addLine(
    into: ArrayList<StreetSegment>,
    frame: TownFrame,
    through: Vec2d,
    direction: Vec2d,
    halfLength: Double,
    rank: Int,
    step: Double
  ) {
    var s = -halfLength
    while (s < halfLength) {
      val next = min(halfLength, s + step)
      val a = through + direction * s
      val b = through + direction * next
      if (frame.encloses(a) && frame.encloses(b)) into.add(StreetSegment(a, b, rank))
      s = next
    }
  }

  // --- Planarisation --------------------------------------------------------------------------------

  /**
   * Splits every crossing and welds coincident endpoints, producing a graph whose edges meet only at nodes.
   *
   * This is the step the face traversal depends on absolutely. Two segments that cross without sharing a
   * node leave the traversal free to walk through the crossing, and what comes back is one face wrapping
   * the whole town - so the town gets a single enormous "block" and no buildings. Welding is on a
   * millimetre grid via [Quantize] rather than by float equality, for the usual reason: this is a decision
   * about whether two positions are the same place, and decisions do not get to be made on raw floats.
   *
   * ### One pass is not enough, and the reason is welding
   *
   * Measured over sixty seeds of an organic town, a single pass left **two to five per cent of seeds
   * non-planar at every `minRadials` value** - and the single-seed test guarding this had simply drawn a good
   * seed. The mechanism is the welding itself rather than the crossing arithmetic: snapping a node to the
   * [WELD_UNITS] grid moves it by up to half a cell, and merging two nearby nodes moves an edge's end further
   * still, so an edge can be dragged across a third edge it previously missed. Splitting cannot see a crossing
   * that splitting created.
   *
   * So the pass runs to a **fixed point**: split, weld, and if any crossing survives, feed the resulting edges
   * back in. Snap-rounding converges quickly because each round's displacement is bounded by half a weld cell
   * and the surviving crossings are the ones that displacement created; [PLANARISE_ROUNDS] is a backstop, not
   * an expectation. The measured round count is in the test.
   *
   * A crossing is also snapped **once** and the same [Vec2d] handed to both segments, so the two sides share a
   * node by construction rather than by two roundings agreeing. On the sixty seeds above that changed nothing
   * measurable on its own - the iteration is what fixes them - but it removes a hazard that only shows when a
   * crossing lands near a cell boundary, which is precisely the case nothing else here would catch.
   */
  private fun planarise(segments: List<StreetSegment>): StreetGraph {
    var current = segments
    repeat(PLANARISE_ROUNDS) {
      val graph = planariseOnce(current)
      if (!graph.hasCrossing()) return graph
      current = graph.edges.map { StreetSegment(graph.nodes[it.a], graph.nodes[it.b], it.rank) }
    }
    return planariseOnce(current)
  }

  private fun planariseOnce(segments: List<StreetSegment>): StreetGraph {
    // Split points per segment, keyed by parameter along it so they stay in order, valued by the *shared*
    // welded position. 0 and 1 are always present, as the segment's own endpoints.
    val splits = Array(segments.size) { i -> sortedMapOf(0.0 to segments[i].a, 1.0 to segments[i].b) }

    for (i in segments.indices) {
      for (j in i + 1 until segments.size) {
        val hit = Intersections.segmentCrossing(
          segments[i].a, segments[i].b, segments[j].a, segments[j].b
        ) ?: continue

        val shared = Vec2d(Quantize.snap(hit.first.x, WELD_UNITS), Quantize.snap(hit.first.y, WELD_UNITS))
        splits[i][hit.second] = shared
        splits[j][hit.third] = shared
      }
    }

    val nodes = ArrayList<Vec2d>()
    val byKey = HashMap<Long, Int>()

    fun nodeAt(p: Vec2d): Int {
      val key = (Quantize.toFixed(p.x, WELD_UNITS) shl 32) xor
          (Quantize.toFixed(p.y, WELD_UNITS) and 0xFFFF_FFFFL)
      return byKey.getOrPut(key) {
        nodes.add(Vec2d(Quantize.snap(p.x, WELD_UNITS), Quantize.snap(p.y, WELD_UNITS)))
        nodes.size - 1
      }
    }

    val edges = ArrayList<StreetGraph.Edge>()
    val seen = HashSet<Long>()

    for (i in segments.indices) {
      val segment = segments[i]
      // In parameter order, so consecutive values are consecutive along the segment. The values are the
      // welded crossing positions, shared with whichever segment produced each one.
      val cuts = splits[i].values.toList()

      for (k in 0 until cuts.size - 1) {
        val a = nodeAt(cuts[k])
        val b = nodeAt(cuts[k + 1])
        if (a == b) continue

        // A duplicate edge would appear twice in the incident ring and send the face traversal round a
        // zero-area loop forever.
        val key = if (a < b) a.toLong() shl 32 or b.toLong() else b.toLong() shl 32 or a.toLong()
        if (!seen.add(key)) continue

        edges.add(StreetGraph.Edge(a, b, segment.rank))
      }
    }

    return StreetGraph(nodes, edges)
  }

  /**
   * Welding resolution, in units per metre.
   *
   * Coarser than [Quantize.PER_METRE] on purpose. Two segments crossing at a shallow angle put their
   * intersection points a fraction of a millimetre apart on each side, and at millimetre resolution those
   * are two nodes - which leaves a zero-length gap exactly where the graph most needs to be connected.
   * Ten centimetres is far below anything a street cares about and far above the error.
   */
  private const val WELD_UNITS = 10.0

  /**
   * Cap on planarisation rounds, as a backstop against a cycle rather than an expected count.
   *
   * Each round's displacement is bounded by half a weld cell, so the crossings a round can create are strictly
   * smaller-scale than the ones it removed and the process converges. Measured across three hundred and sixty
   * organic towns, no seed needed more than two rounds. Four is where a pathological case is called off rather
   * than allowed to spin - and it is called off with a graph, not an exception, because a town with one bad
   * junction is worth having and a town that throws is not.
   */
  private const val PLANARISE_ROUNDS = 4

  /** How many grown segments make one grid block. A block wants to be a few buildings long. */
  private const val GRID_BLOCK_MULTIPLE = 2.0

  private const val WANDER_SALT = 0x31L
  private const val BRANCH_SALT = 0x32L
  private const val SIDE_SALT = 0x33L
  private const val TURN_SALT = 0x34L
  private const val RADIAL_SALT = 0x35L
  private const val GRID_SALT = 0x36L
  private const val RING_PHASE_SALT = 0x37L

  // 0x38 is TownBoundary.ASPECT_SALT. The salts in this file share one keyed roll, so they share one space.
  private const val ARC_SPAN_SALT = 0x39L
  private const val ARC_START_SALT = 0x3AL

  /** How far a cross street strays from its nominal radius, as a fraction of it. */
  private const val RING_WANDER = 0.16

  /**
   * Bounds on a rolled arc span, as shares of a turn.
   *
   * The ceiling is the load-bearing one: jitter must never be able to roll an arc back up to a full turn, or the
   * ring road this replaced reappears on whichever seeds happen to roll high. The floor keeps an arc long enough
   * to cross more than one radial, which is what makes it close a block rather than dead-end twice.
   */
  private const val MIN_ARC_SPAN = 0.2
  private const val MAX_ARC_SPAN = 0.85

  private fun rotate(v: Vec2d, radians: Double): Vec2d {
    val c = cos(radians)
    val s = sin(radians)
    return Vec2d(v.x * c - v.y * s, v.x * s + v.y * c)
  }
}

/**
 * One building plot: a rectangle with its front on a street.
 *
 * ### Deviation from the design, and the bug that produced it
 *
 * The architecture document goes street graph -> *faces* -> blocks -> recursive subdivision into lots, then
 * asks for a check that every lot has street frontage. This goes street graph -> lots directly: plots are
 * laid along both sides of every street by arc length, and rejected where they would overlap another plot or
 * reach across a street behind them.
 *
 * The first version did it the document's way, and the `town` tool found what was wrong with it. Faces exist
 * only because the ring streets close them, so a river crossing the town removed a few ring segments, broke
 * each ring's cycle, and a broken ring encloses nothing: one channel took a city from 574 plots to 68. Every
 * plot the town lost was on a block whose boundary the river had merely nicked. Worse, the two outcomes look
 * the same on a map, and both look like a town.
 *
 * Fronting the streets directly has no such failure mode. Losing a street segment costs exactly the plots on
 * that segment, which is the correct answer, and frontage remains a property of the construction rather than
 * something to verify. Blocks stop being an object - what is left in the middle of four streets is whatever
 * the plots did not reach, which is the yards and gardens that were there anyway.
 *
 * What is lost is a *block* as something to reason about: zoning a whole block as a craft quarter, or putting
 * a market in the one open face at the centre. `StreetGraph.faces` and its half-edge traversal were deleted
 * rather than left in place, on the grounds that unused machinery is a liability - the planarisation it needed
 * stays, because the chains and the overlap tests need it too.
 */
internal class Lot(
  /** Centre of the plot. */
  val centre: Vec2d,
  /** Unit direction from the street into the block. The building faces the other way. */
  val inwards: Vec2d,
  /** Half-extent along the street. */
  val halfFrontage: Double,
  /** Half-extent into the block. */
  val halfDepth: Double,
  /** Rank of the street it fronts; zero is the high street. */
  val streetRank: Int,
  /**
   * Walking distance from the market along the streets, normalised to `[0,1]`. See [StreetDistance].
   *
   * This was `fromCentre`, in metres as the crow flies, and every use of it divided by `builtRadius` to get
   * back to a fraction - so the radius was doing two jobs and the value was radial by construction. Normalised
   * here instead, once, by the thing that actually knows the town's extent.
   */
  val fromMarket: Double
) {
  val area get() = halfFrontage * halfDepth * 4.0

  /** Half-extent of this plot projected onto [axis]. The radius term of a separating-axis test. */
  fun extentAlong(axis: Vec2d): Double =
    abs(halfFrontage * (inwards.perpendicular() dot axis)) + abs(halfDepth * (inwards dot axis))
}

/** Lays plots along both sides of every street. See [Lot] for why it is done this way round. */
internal object LotPlanner {

  /**
   * @param already plots some other producer has claimed - the patched core's, when a town has one. Seeded into
   *   the overlap index rather than merged afterwards, so a street plot that would land on a block plot is
   *   rejected at the point it is considered. The core is laid first because it is the part a player walks
   *   through; the suburbs then fill whatever frontage is left, which is what a suburb is.
   * @param distance the walking distance field, shared with whoever laid [already] so that both halves of a town
   *   measure land value against the same network.
   */
  fun subdivide(
    graph: StreetGraph,
    frame: TownFrame,
    frontage: Double,
    depth: Double,
    setback: Double,
    distance: StreetDistance = StreetDistance(graph, frame.centre),
    already: List<Lot> = emptyList(),
    /**
     * Ground this producer must leave alone - the patched core, when a town has one.
     *
     * Necessary, and the overlap index is not enough on its own. A block is set back from its patch edge, so there
     * is a strip of open ground along every street in the core that a street-fronted plot fits into without
     * overlapping anything. The first version left it out, and the result was that the *suburb* path quietly filled
     * the core: a quarter laid out as a park, with eighty per cent of its plots deliberately left as green, came
     * back with fifty buildings in it and every quarter's grain was overwritten by an even row of street frontage.
     */
    skip: (Vec2d) -> Boolean = { false }
  ): List<Lot> {
    val out = ArrayList<Lot>()

    // Sized to the largest plot the index will hold, not to a street plot. The index checks a three-by-three
    // window around a candidate's own cell, so a placed plot bigger than one cell can overlap a candidate whose
    // centre lies outside that window and never be found - and a block plot from a patrician quarter or a citadel
    // is several times a street plot. Cheap insurance: the cell only decides how many plots share a bucket.
    val cell = max(
      depth * 2.0 + frontage,
      already.maxOfOrNull { (it.halfFrontage + it.halfDepth) * 2.0 } ?: 0.0
    )
    val placed = LotIndex(cell)
    for (lot in already) placed.add(lot)
    val reach = setback + depth

    // Highest-rank streets first, so the high street gets its frontage before a lane can take a bite out of
    // it. Without the ordering a rank-3 back lane laid earlier would block the plots on the main street.
    for ((chain, rank) in graph.chains().sortedBy { it.second }) {
      var s = 0.0
      while (s + frontage <= chain.length) {
        val middle = s + frontage * 0.5
        val at = chain.pointAt(middle)
        val along = chain.tangentAt(middle)
        s += frontage

        for (side in SIDES) {
          val inwards = along.perpendicular() * side
          val centre = at + inwards * (setback + depth * 0.5)

          val lot = Lot(
            centre = centre,
            inwards = inwards,
            halfFrontage = frontage * 0.5 * LOT_GAP,
            halfDepth = depth * 0.5,
            streetRank = rank,
            // Measured at the plot's *front*, where it meets the street, rather than at its centre: the centre
            // is a lot depth back into the block and off the network the distance is measured along.
            fromMarket = distance.at(at)
          )

          if (skip(centre)) continue
          if (!frame.buildable(centre)) continue
          if (placed.overlaps(lot)) continue
          // A plot must not reach over the street behind it. Without this, two streets fifteen metres apart
          // would each grow plots through the other, and a building would sit in the middle of a road.
          if (crossesAStreet(lot, graph, at, reach)) continue

          placed.add(lot)
          out.add(lot)
        }
      }
    }

    return out
  }

  /**
   * Whether a street runs through this plot. For a plot some other producer laid.
   *
   * The patched core cuts its plots out of a patch, which knows the streets on the patch's own *edges* and nothing
   * about the grown streets that cross its middle on the way out of town. So a block plot could sit squarely on a
   * radial, which `TownStageTest."streets are paved and buildings are not on them"` caught - a building standing on
   * a paved road, which is the one thing about a town nobody could miss.
   *
   * The plot's own fronting street is excluded the same way it is for a street-laid plot: by construction, since
   * the block was set back from the patch edge before being cut.
   */
  fun blockedByStreet(lot: Lot, graph: StreetGraph): Boolean {
    val front = lot.centre - lot.inwards * lot.halfDepth
    return crossesAStreet(lot, graph, front, lot.halfDepth * 2.0)
  }

  /**
   * Whether any street segment other than the one this plot fronts passes through it.
   *
   * The fronting street is excluded by construction rather than by identity: the plot starts [setback] metres
   * back from its own centreline, so its own street cannot intersect it, and any segment that does is a
   * different one.
   */
  private fun crossesAStreet(lot: Lot, graph: StreetGraph, front: Vec2d, reach: Double): Boolean {
    for ((a, b) in graph.segmentsNear(front, reach + lot.halfFrontage)) {
      if (segmentHitsLot(lot, a, b)) return true
    }
    return false
  }

  /**
   * Segment against oriented rectangle, in the rectangle's own axes.
   *
   * Transforming the segment into local coordinates turns the oriented test into an axis-aligned one, which is
   * the standard slab clip and about ten lines. Doing it the other way - rotating the rectangle into world
   * axes - is not possible, which is the whole reason oriented boxes are tested this way.
   */
  private fun segmentHitsLot(lot: Lot, a: Vec2d, b: Vec2d): Boolean {
    val along = lot.inwards.perpendicular()

    fun local(p: Vec2d): Vec2d {
      val d = p - lot.centre
      return Vec2d(d dot along, d dot lot.inwards)
    }

    val p = local(a)
    val q = local(b)
    val hx = lot.halfFrontage
    val hy = lot.halfDepth

    // Both ends on the same outside of a slab: separated, so no hit.
    if (p.x < -hx && q.x < -hx) return false
    if (p.x > hx && q.x > hx) return false
    if (p.y < -hy && q.y < -hy) return false
    if (p.y > hy && q.y > hy) return false

    // Either end inside is a hit outright.
    if (abs(p.x) <= hx && abs(p.y) <= hy) return true
    if (abs(q.x) <= hx && abs(q.y) <= hy) return true

    // Otherwise the segment crosses the box only if the box's corners straddle its line.
    val d = q - p
    var negative = false
    var positive = false
    for (sx in SIGNS) {
      for (sy in SIGNS) {
        val corner = Vec2d(sx * hx, sy * hy)
        val side = d cross (corner - p)
        if (side < 0.0) negative = true else if (side > 0.0) positive = true
      }
    }
    return negative && positive
  }

  private val SIDES = doubleArrayOf(1.0, -1.0)
  private val SIGNS = doubleArrayOf(-1.0, 1.0)

  /**
   * Fraction of its frontage a plot's building actually occupies, leaving a gap between neighbours.
   *
   * One of three multipliers between `TownParams.lotFrontage` and a building's width - see the note there.
   * Raised from 0.86 when the plots grew: a wider plot can afford a proportionally narrower gap and still
   * leave two metres of air between neighbours, which is what a street of separate houses needs.
   */
  private const val LOT_GAP = 0.92
}

/** A keyed roll for the layout, folding the world seed, the stage and the settlement. */
internal fun townRoll(streamBase: Long, settlement: Int): (Long, Long) -> Double =
  { a, b -> GenRng.hashUnit(streamBase, settlement.toLong(), a, b) }
