package net.bestia.worldgen.civ

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.vector.Intersections
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Quantize
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
  /** Approximate ground elevation, already terraced the way the settlement's grading will terrace it. */
  val groundAt: (Vec2d) -> Double,
  /** False over water, over ground too steep to build on, or outside the world. */
  val buildable: (Vec2d) -> Boolean,
  /** Unit directions from which roads arrive. Empty for a place no road reaches. */
  val approaches: List<Vec2d>
)

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

/** Tuning for the street layout. */
internal class StreetParams(
  /** Metres per grown segment. Also the grid's block size. */
  val segmentLength: Double = 34.0,
  /** Radians a grown segment may wander from its parent's heading. */
  val angleJitter: Double = 0.28,
  /** Chance a grown segment throws a side street. */
  val branchChance: Double = 0.28,
  /** Metres within which a growing street snaps to an existing node instead of passing it. */
  val snapRadius: Double = 11.0,
  /** Fewest radial streets, however few roads arrive. A town with one road still has a crossroads. */
  val minRadials: Int = 4,
  val maxRadials: Int = 7,
  /**
   * Ring streets, as fractions of the built radius.
   *
   * Three, not two, and spread inwards. With rings at 0.42 and 0.82 the plots concentrated on the two rings -
   * they are far the longest chains in the network - and the middle of the town came out nearly empty, so a
   * rendered town read as two concentric terraces with a field between them. A real town is densest at its
   * centre, and the cheapest way to get that is to give the centre streets to front onto.
   */
  val rings: DoubleArray = doubleArrayOf(0.28, 0.55, 0.82),
  /** Vertices per ring. Enough that a ring reads as a curve rather than a polygon. */
  val ringVertices: Int = 20,
  /** Deepest a grown street may go, in segments. */
  val maxDepth: Int = 9
)

/**
 * Lays out a town's streets: agent growth for an organic town, a clipped grid for a planned one.
 *
 * Both end in the same place - a set of segments handed to [planarise] - and that is the point. The two
 * algorithms differ in what they produce, not in what happens to it, so the block extraction, the lots and
 * the buildings are written once.
 */
internal object StreetPlanner {

  fun plan(
    frame: TownFrame,
    layout: TownLayout,
    /** Keyed roll, `(salt...) -> [0,1)`. Never a stream: see [net.bestia.worldgen.history.HistorySim]. */
    roll: (Long, Long) -> Double,
    params: StreetParams = StreetParams()
  ): StreetGraph {
    val raw = when (layout) {
      TownLayout.ORGANIC -> organic(frame, roll, params)
      TownLayout.GRID -> grid(frame, roll, params)
    }

    return planarise(raw.filter { passable(frame, it) })
  }

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
   * Radials out of the market square, side streets branching off them, and two ring roads.
   *
   * The rings are not decoration. A pure branching growth is a *tree*, and a tree has no cycles, so face
   * traversal finds no bounded faces and the town gets no blocks and therefore no buildings at all. Rings
   * guarantee cycles; snapping produces more of them opportunistically. That failure is worth naming
   * because it does not look like a missing ring - it looks like a town with streets and nothing on them.
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

      if (end.distanceTo(frame.centre) > frame.builtRadius) continue

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

    out.addAll(ringSegments(frame, params, rank = 1, roll = roll))
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
   * Ring streets at fractions of the built radius, wandering rather than circular.
   *
   * The wander is not decoration. Exact circles produce a town that reads as a diagram of a town from above -
   * three concentric terraces at surveyed radii, which no place grown from a crossroads has ever looked like.
   * Perturbing each vertex radially by a smooth function of its angle costs six lines and is the difference
   * between a garden city and a market town.
   *
   * The perturbation is a sum of two harmonics rather than per-vertex noise, because a ring has to stay a
   * closed smooth curve: independent jitter per vertex gives a ring with corners in it, and a street with
   * corners every forty metres is a worse artefact than a circle.
   */
  private fun ringSegments(
    frame: TownFrame,
    params: StreetParams,
    rank: Int,
    roll: (Long, Long) -> Double
  ): List<StreetSegment> {
    val out = ArrayList<StreetSegment>()

    for ((index, fraction) in params.rings.withIndex()) {
      val radius = frame.builtRadius * fraction
      // A ring smaller than a block is the market square, not a street.
      if (radius < params.segmentLength) continue

      val phaseA = roll(index.toLong(), RING_PHASE_SALT) * 2.0 * PI
      val phaseB = roll(index.toLong(), RING_PHASE_SALT + 1) * 2.0 * PI
      val wander = { turn: Double ->
        val angle = turn * 2.0 * PI
        radius * (1.0 + RING_WANDER * (sin(angle * 2.0 + phaseA) + 0.6 * sin(angle * 3.0 + phaseB)) / 1.6)
      }

      val vertices = params.ringVertices
      for (i in 0 until vertices) {
        val ta = i.toDouble() / vertices
        val tb = (i + 1).toDouble() / vertices
        out.add(
          StreetSegment(
            onCircle(frame.centre, wander(ta), ta),
            onCircle(frame.centre, wander(tb), tb),
            rank
          )
        )
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
    val reach = frame.builtRadius
    val lines = (reach / spacing).toInt()

    val out = ArrayList<StreetSegment>()

    for (i in -lines..lines) {
      val offset = i * spacing
      // Half-chord of the circle at this offset: what clips the grid to a round town instead of a square one.
      val half = sqrt(max(0.0, reach * reach - offset * offset))
      if (half < spacing * 0.5) continue

      // Avenues along the axis, streets across it. Rank 0 for the centre line of each family, so the two
      // main streets meeting at the market are the important ones.
      val rankAlong = if (i == 0) 0 else 1
      addLine(out, frame.centre + across * offset, axis, half, rankAlong, spacing)
      addLine(out, frame.centre + axis * offset, across, half, if (i == 0) 0 else 2, spacing)
    }

    return out
  }

  private fun addLine(
    into: ArrayList<StreetSegment>,
    through: Vec2d,
    direction: Vec2d,
    halfLength: Double,
    rank: Int,
    step: Double
  ) {
    var s = -halfLength
    while (s < halfLength) {
      val next = min(halfLength, s + step)
      into.add(StreetSegment(through + direction * s, through + direction * next, rank))
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
   */
  private fun planarise(segments: List<StreetSegment>): StreetGraph {
    // Split points per segment, as parameters along it. 0 and 1 are always present.
    val splits = Array(segments.size) { sortedSetOf(0.0, 1.0) }

    for (i in segments.indices) {
      for (j in i + 1 until segments.size) {
        val hit = Intersections.segmentCrossing(
          segments[i].a, segments[i].b, segments[j].a, segments[j].b
        ) ?: continue
        splits[i].add(hit.second)
        splits[j].add(hit.third)
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
      val cuts = splits[i].toList()

      for (k in 0 until cuts.size - 1) {
        val a = nodeAt(segment.a.lerp(segment.b, cuts[k]))
        val b = nodeAt(segment.a.lerp(segment.b, cuts[k + 1]))
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

  /** How many grown segments make one grid block. A block wants to be a few buildings long. */
  private const val GRID_BLOCK_MULTIPLE = 2.0

  private const val WANDER_SALT = 0x31L
  private const val BRANCH_SALT = 0x32L
  private const val SIDE_SALT = 0x33L
  private const val TURN_SALT = 0x34L
  private const val RADIAL_SALT = 0x35L
  private const val GRID_SALT = 0x36L
  private const val RING_PHASE_SALT = 0x37L

  /** How far a ring street strays from its nominal radius, as a fraction of it. */
  private const val RING_WANDER = 0.16

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
  /** Metres from the town centre. */
  val fromCentre: Double
) {
  val area get() = halfFrontage * halfDepth * 4.0

  /** Half-extent of this plot projected onto [axis]. The radius term of a separating-axis test. */
  fun extentAlong(axis: Vec2d): Double =
    abs(halfFrontage * (inwards.perpendicular() dot axis)) + abs(halfDepth * (inwards dot axis))
}

/** Lays plots along both sides of every street. See [Lot] for why it is done this way round. */
internal object LotPlanner {

  fun subdivide(
    graph: StreetGraph,
    frame: TownFrame,
    frontage: Double,
    depth: Double,
    setback: Double
  ): List<Lot> {
    val out = ArrayList<Lot>()
    val placed = LotIndex(depth * 2.0 + frontage)
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
            fromCentre = centre.distanceTo(frame.centre)
          )

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

/**
 * A bucket grid over placed plots, for the overlap test.
 *
 * The same shape as the settlement stage's separation index and for the same reason: a town lays a few
 * thousand candidate plots and rejects most of them for touching one already there, and testing each against
 * every placed plot is quadratic in the longest loop of the stage.
 */
private class LotIndex(private val cellMetres: Double) {

  private val buckets = HashMap<Long, ArrayList<Lot>>()

  fun add(lot: Lot) {
    buckets.getOrPut(keyOf(lot.centre)) { ArrayList() }.add(lot)
  }

  fun overlaps(lot: Lot): Boolean {
    val bx = Math.floor(lot.centre.x / cellMetres).toLong()
    val by = Math.floor(lot.centre.y / cellMetres).toLong()

    for (dy in -1..1) {
      for (dx in -1..1) {
        val bucket = buckets[key(bx + dx, by + dy)] ?: continue
        for (other in bucket) {
          if (intersects(lot, other)) return true
        }
      }
    }
    return false
  }

  /**
   * Oriented-box intersection by the separating-axis theorem.
   *
   * Four axes - each box's two - and the boxes are apart if any one of them separates them. A bounding-circle
   * test would be far simpler and is not usable here: consecutive plots on the same street are nine metres
   * apart and their circumscribed circles are nine metres across, so a circle test rejects every plot's own
   * neighbour and a town comes out with every other plot empty.
   */
  private fun intersects(a: Lot, b: Lot): Boolean {
    val axes = arrayOf(a.inwards.perpendicular(), a.inwards, b.inwards.perpendicular(), b.inwards)
    for (axis in axes) {
      val centreGap = abs((b.centre - a.centre) dot axis)
      val spread = a.extentAlong(axis) + b.extentAlong(axis)
      if (centreGap > spread) return false
    }
    return true
  }

  private fun keyOf(at: Vec2d) =
    key(Math.floor(at.x / cellMetres).toLong(), Math.floor(at.y / cellMetres).toLong())

  private fun key(bx: Long, by: Long) = (bx shl 32) xor (by and 0xFFFF_FFFFL)
}

/** A keyed roll for the layout, folding the world seed, the stage and the settlement. */
internal fun townRoll(streamBase: Long, settlement: Int): (Long, Long) -> Double =
  { a, b -> GenRng.hashUnit(streamBase, settlement.toLong(), a, b) }
