package net.bestia.worldgen.civ

import net.bestia.worldgen.vector.ConvexPolygons
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Ring
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A village laid along the road that passes through it.
 *
 * A village is not a small town. It has no centre to radiate from, and the street it is built on is the
 * region's road rather than a street of its own - so the road's geometry becomes the spine verbatim, a few
 * lanes hang off it, and the built edge is what those streets reach rather than a circle drawn first.
 *
 * [of] returns null where the model does not fit: no road passes, or the settlement wants more frontage than a
 * spine and a handful of lanes can carry. Both fall back to the grown layout, which is what a place that large
 * or that landlocked actually is.
 */
internal class RoadsideVillage private constructor(
  val segments: List<StreetSegment>,
  val boundary: Ring,
  /** Furthest [boundary] reaches from the settlement's centre. The caller's built radius. */
  val reach: Double,
  /** The form this village actually took, which is not always the one its culture asked for. */
  val form: VillageForm,
  /** The common a [VillageForm.GREEN] village encloses, as a convex polygon. Empty for the other forms. */
  val green: List<Vec2d>
) {

  /** Whether a position is on the common, where the lot planner must leave the ground open. */
  fun onTheGreen(at: Vec2d): Boolean {
    return green.size >= 3 && ConvexPolygons.contains(green, at)
  }

  companion object {

    /**
     * @param bound furthest from [centre] any street may run, so every lot stays on ground the settlement graded.
     * @param buildings how many buildings the settlement is sized for, which is what fixes how much street it needs.
     */
    fun of(
      centre: Vec2d,
      bound: Double,
      buildings: Int,
      roads: List<Polyline>,
      /**
       * The way through the settlement when no road qualifies.
       *
       * Villages and hamlets are off the road network by design - `SettlementStage.buildRoads` connects only
       * cities and towns - so for most of them this, not [roads], is what the village is built along.
       */
      track: Polyline,
      /** The form this settlement's culture builds. Downgraded where the ways cannot support it. */
      wanted: VillageForm,
      buildable: (Vec2d) -> Boolean,
      roll: (Long, Long) -> Double,
      params: TownParams
    ): RoadsideVillage? {
      if (buildings < 1 || bound <= params.streets.segmentLength) return null

      val through = roadsThrough(centre, roads, bound).ifEmpty { listOf(track) }

      val needed = buildings * metresOfStreetPerBuilding(params)
      // Floored, because a spine shorter than a couple of segments is walked away to nothing and the
      // settlement comes back with no street at all. Four houses still stand along *some* length of way.
      val spineLength = min(
        max(needed * params.streets.roadsideSpineShare, params.streets.segmentLength * MIN_SPINE_STEPS),
        bound * 2.0
      )
      if (spineLength + MAX_LANES * MAX_LANE_LENGTH < needed * MIN_COVERAGE) return null

      val spine = spineAlong(through[0], centre, spineLength, bound, buildable, params) ?: return null

      // The ways decide first: a second way that crosses makes a crossroads whatever the culture builds, and
      // a green needs enough spine to part around one.
      val form = when {
        through.size >= 2 -> VillageForm.CROSSROADS
        wanted == VillageForm.GREEN && spine.length >= MIN_GREEN_SPINE -> VillageForm.GREEN
        else -> VillageForm.LINEAR
      }

      val segments = ArrayList<StreetSegment>()
      var green: List<Vec2d> = emptyList()

      if (form == VillageForm.GREEN) {
        val common = commonOn(spine, roll, params)
        segments.addAll(common.first)
        green = common.second
      } else {
        segments.addAll(chainOf(spine, SPINE_RANK))
      }

      var budget = needed - spine.length

      if (form == VillageForm.CROSSROADS) {
        // Shorter than the spine, because a village leans along one way however many pass through it.
        val crossing = spineAlong(
          through[1], centre, min(budget, spineLength * CROSS_SHARE), bound, buildable, params
        )
        if (crossing != null) {
          segments.addAll(chainOf(crossing, SPINE_RANK))
          budget -= crossing.length
        }
      }

      segments.addAll(lanesOff(spine, centre, budget, bound, buildable, roll, params))

      val boundary = boundaryAround(segments, params) ?: return null
      return RoadsideVillage(
        segments = segments,
        boundary = boundary,
        reach = ConvexPolygons.reachFrom(boundary.vertices, centre),
        form = form,
        green = green
      )
    }

    /**
     * The way parted around a common and closed again: the two arcs, and the ground between them.
     *
     * Both arcs are streets, so houses front them from outside *and* inside - and the inside row is what makes
     * a green rather than two parallel lanes. The ground itself is handed back so the lot planner can be told
     * to leave it alone; without that the common fills with the houses meant to face it.
     */
    private fun commonOn(
      spine: Polyline,
      roll: (Long, Long) -> Double,
      params: TownParams
    ): Pair<List<StreetSegment>, List<Vec2d>> {
      val length = min(GREEN_MAX_LENGTH, spine.length * GREEN_SHARE)
      val from = (spine.length - length) * 0.5
      val half = length * GREEN_ASPECT *
          (1.0 - GREEN_WIDTH_JITTER + roll(0L, GREEN_WIDTH_SALT) * 2.0 * GREEN_WIDTH_JITTER)

      val steps = max(3, (length / params.streets.segmentLength).toInt())
      val out = ArrayList<StreetSegment>()
      val rim = ArrayList<Vec2d>()

      for (side in doubleArrayOf(1.0, -1.0)) {
        var previous: Vec2d? = null
        for (i in 0..steps) {
          val t = i.toDouble() / steps
          val s = from + length * t
          // A half-sine, so both ends meet the way itself rather than leaving a kink where they rejoin.
          val at = spine.pointAt(s) + spine.tangentAt(s).perpendicular() * (side * half * sin(t * PI))
          previous?.let { out.add(StreetSegment(it, at, SPINE_RANK)) }
          previous = at
          rim.add(at)
        }
      }

      // The stretches of way either side of the common still carry the village.
      if (from > params.streets.segmentLength) {
        out.addAll(chainOf(clip(spine, 0.0, from, params), SPINE_RANK))
      }
      if (spine.length - (from + length) > params.streets.segmentLength) {
        out.addAll(chainOf(clip(spine, from + length, spine.length, params), SPINE_RANK))
      }

      // Hulled rather than taken as the rim in order, because the rim is a lens only while the way is straight
      // and a curved one would hand back a self-crossing outline. Then inset off its own carriageway, so the
      // common is the open ground and the two arcs around it are still streets that can be built along.
      val open = ConvexPolygons.insetAll(ConvexPolygons.hullOf(rim), params.setbackFor(SPINE_RANK))
      return out to open
    }

    private fun clip(line: Polyline, from: Double, to: Double, params: TownParams): Polyline {
      val steps = max(1, ((to - from) / params.streets.segmentLength).toInt())
      val points = (0..steps).map { line.pointAt(from + (to - from) * it / steps) }
      return Polyline(points)
    }

    /**
     * Metres of street one building needs, counting both sides of it.
     *
     * [StreetParams.roadsidePacking] is the share of a frontage that ends up as a building rather than as the
     * gap, the yard and the stretch a slope or a channel took - so this is what decides how strung out a
     * village is, and it is the one number to move if villages come out too tight or too sparse.
     */
    private fun metresOfStreetPerBuilding(params: TownParams): Double {
      return params.lotFrontage / (2.0 * params.streets.roadsidePacking)
    }

    /**
     * The roads the settlement is built on: the nearest, and the nearest one crossing it at a real angle.
     *
     * Two at most. A third road through a village arrives on much the same bearing as one of the first two and
     * would lay a second high street on top of one already there; where genuinely more roads meet, the place
     * grows into a town and stops coming through here at all.
     */
    private fun roadsThrough(centre: Vec2d, roads: List<Polyline>, bound: Double): List<Polyline> {
      // Distance only, and deliberately not `beyondEnd`: a village's own track is a spur that *terminates* at
      // it, so requiring the way to pass through would reject the one way most villages have.
      val near = roads
        .map { it to it.project(centre) }
        .filter { it.second.distance <= bound * ROAD_OFFSET_SHARE }
        .sortedBy { it.second.distance }
      if (near.isEmpty()) return emptyList()

      val spine = near.first()
      val spineHeading = spine.first.tangentAt(spine.second.s)
      // Unsigned, because which way along a road is "forward" is an accident of how it was routed.
      val crossing = near.drop(1).firstOrNull {
        abs(it.first.tangentAt(it.second.s) dot spineHeading) < CROSSING_DISTINCT
      }

      return listOfNotNull(spine.first, crossing?.first)
    }

    /**
     * The stretch of [road] the village is built on, walked outward from the centre until the ground refuses.
     *
     * Walked from the middle rather than clipped at two arc lengths because a village stops where the road
     * leaves the graded ground or crosses something unbuildable, and that can happen on either side
     * independently - a clip would keep the far bank and drop the near one.
     */
    private fun spineAlong(
      road: Polyline,
      centre: Vec2d,
      length: Double,
      bound: Double,
      buildable: (Vec2d) -> Boolean,
      params: TownParams
    ): Polyline? {
      val middle = road.project(centre).s
      val step = params.streets.segmentLength
      val half = length * 0.5

      val points = ArrayDeque<Vec2d>()
      points.addLast(road.pointAt(middle))

      for (side in intArrayOf(1, -1)) {
        var walked = step
        while (walked <= half) {
          val s = middle + side * walked
          // Past the end of the way, the village street carries straight on as a farm track. Without this a
          // spur that terminates at the settlement - which is what a village's own track is - builds one
          // half of a village and stops dead at the junction.
          val at = if (s < 0.0 || s > road.length) {
            val end = if (s < 0.0) 0.0 else road.length
            road.pointAt(end) + road.tangentAt(end) * (s - end)
          } else {
            road.pointAt(s)
          }

          if (at.distanceTo(centre) > bound || !buildable(at)) break
          if (side > 0) points.addLast(at) else points.addFirst(at)
          walked += step
        }
      }

      if (points.size < 2) return null
      return runCatching { Polyline(points.toList()) }.getOrNull()
    }

    /**
     * Lanes off the spine, sharing whatever frontage the spine could not carry.
     *
     * Straight, because a village lane is a few houses long and has no room to bend; the variety comes from
     * where they leave and at what angle rather than from wander along them.
     */
    private fun lanesOff(
      spine: Polyline,
      centre: Vec2d,
      budget: Double,
      bound: Double,
      buildable: (Vec2d) -> Boolean,
      roll: (Long, Long) -> Double,
      params: TownParams
    ): List<StreetSegment> {
      if (budget < MIN_LANE_LENGTH) return emptyList()

      val count = min(MAX_LANES, max(1, ceil(budget / MAX_LANE_LENGTH).toInt()))
      val each = min(MAX_LANE_LENGTH, budget / count)
      val step = params.streets.segmentLength

      // Alternating rather than rolled per lane: two lanes that roll the same side leave from the same stretch
      // of spine and converge, which reads as one forked street rather than as two lanes.
      val first = if (roll(0L, LANE_SIDE_SALT) < 0.5) 0 else 1

      val out = ArrayList<StreetSegment>()
      for (i in 0 until count) {
        val salt = i.toLong()
        // Spread over the spine's middle, so no lane leaves from its very end where there is nothing to serve.
        val share = (i + 0.5) / count
        val jittered = share + (roll(salt, LANE_WHERE_SALT) - 0.5) * LANE_WHERE_JITTER
        val s = spine.length * jittered.coerceIn(LANE_INSET, 1.0 - LANE_INSET)

        val from = spine.pointAt(s)
        val side = if ((i + first) % 2 == 0) 1.0 else -1.0
        val skew = (roll(salt, LANE_SKEW_SALT) - 0.5) * 2.0 * LANE_SKEW
        val heading = (spine.tangentAt(s).perpendicular() * side).rotated(skew)
        val length = each * (1.0 - LANE_LENGTH_JITTER + roll(salt, LANE_LENGTH_SALT) * 2.0 * LANE_LENGTH_JITTER)

        var walked = step
        var previous = from
        while (walked <= length) {
          val at = from + heading * walked
          if (at.distanceTo(centre) > bound || !buildable(at)) break
          out.add(StreetSegment(previous, at, LANE_RANK))
          previous = at
          walked += step
        }
      }

      return out
    }

    private fun chainOf(line: Polyline, rank: Int): List<StreetSegment> {
      val out = ArrayList<StreetSegment>(line.segmentCount)
      for (i in 0 until line.segmentCount) {
        out.add(StreetSegment(line.points[i], line.points[i + 1], rank))
      }
      return out
    }

    /**
     * The built edge: the convex hull of the ground the streets can put a lot on.
     *
     * A hull of discs around every street end rather than an offset of a polygon, because offsetting has a
     * corner case at every sharp vertex and a hull of points has none - and the hull of a convex set of discs
     * is exactly the reach a lot laid off those streets can have. Convex also means [Ring]'s self-intersection
     * check can never reject it, whatever shape the road through the village takes.
     */
    private fun boundaryAround(segments: List<StreetSegment>, params: TownParams): Ring? {
      if (segments.isEmpty()) return null

      val offset = params.setbackFor(0) + params.lotDepth + BOUNDARY_MARGIN
      val points = ArrayList<Vec2d>(segments.size * 2 * BOUNDARY_ARC)
      for (segment in segments) {
        for (end in listOf(segment.a, segment.b)) {
          for (i in 0 until BOUNDARY_ARC) {
            val angle = i * 2.0 * PI / BOUNDARY_ARC
            points.add(end + Vec2d(cos(angle), sin(angle)) * offset)
          }
        }
      }

      val hull = ConvexPolygons.hullOf(points)
      if (hull.size < 3) return null

      return runCatching { Ring(decimated(hull)) }.getOrNull()
    }

    /** [hull] thinned to what a [Ring] will hold, keeping its shape by dropping evenly around it. */
    private fun decimated(hull: List<Vec2d>): List<Vec2d> {
      if (hull.size <= Ring.MAX_VERTICES) return hull

      val out = ArrayList<Vec2d>(Ring.MAX_VERTICES)
      for (i in 0 until Ring.MAX_VERTICES) {
        out.add(hull[i * hull.size / Ring.MAX_VERTICES])
      }
      return out
    }

    /** The road through the village is its high street; a lane off it is not. */
    private const val SPINE_RANK = 0
    private const val LANE_RANK = 1

    /**
     * Most lanes a village gets, and the longest one.
     *
     * Together with the spine these fix how much frontage the model can carry, and [of] refuses a settlement
     * that wants more - so raising them does not make villages denser, it makes larger places stay villages.
     */
    private const val MAX_LANES = 4
    private const val MAX_LANE_LENGTH = 95.0
    private const val MIN_LANE_LENGTH = 30.0

    /** Share of the frontage a settlement wants that the layout must carry before it is used at all. */
    private const val MIN_COVERAGE = 0.8

    /** How far off the road's line the settlement's centre may sit, as a share of its bound. */
    private const val ROAD_OFFSET_SHARE = 0.5

    /** Length of a crossing road's built stretch, as a share of the spine's. */
    private const val CROSS_SHARE = 0.6

    /**
     * The common: how much of the spine it takes, how long it may get, and how fat the lens is.
     *
     * A green wide enough to stand in and short enough that the two arcs still read as one street parting -
     * a third of its length across is the proportion an English village green actually has.
     */
    private const val GREEN_SHARE = 0.45
    private const val GREEN_MAX_LENGTH = 160.0
    private const val GREEN_ASPECT = 0.17
    private const val GREEN_WIDTH_JITTER = 0.25

    /**
     * Spine below which a green would take the whole village, leaving no street to arrive on.
     *
     * A hamlet's spine is capped by the ground its settlement graded, which at that tier is about 190 m before
     * terrain takes its share - so a threshold much above this is one no hamlet ever clears, and the form
     * would be unreachable rather than rare.
     */
    private const val MIN_GREEN_SPINE = 110.0

    /** Segments of way the smallest settlement still gets, so a hamlet has a street rather than a point. */
    private const val MIN_SPINE_STEPS = 3.0

    /** How far from parallel a second road must run to count as a crossing, as a dot product. About 35 degrees. */
    private const val CROSSING_DISTINCT = 0.82

    /** Closest to either end of the spine a lane may leave, as a share of its length. */
    private const val LANE_INSET = 0.15
    private const val LANE_WHERE_JITTER = 0.25
    private const val LANE_LENGTH_JITTER = 0.3

    /** How far a lane may lean off square to the spine, in radians. About twenty degrees. */
    private const val LANE_SKEW = 0.35

    /** Metres of open ground kept beyond the last lot, so the edge is not drawn on the building line. */
    private const val BOUNDARY_MARGIN = 6.0

    /** Points per disc in the hull. Eight is under a metre of error at the offsets a village uses. */
    private const val BOUNDARY_ARC = 8

    private const val LANE_WHERE_SALT = 0x61L
    private const val LANE_SIDE_SALT = 0x62L
    private const val LANE_SKEW_SALT = 0x63L
    private const val LANE_LENGTH_SALT = 0x64L
    private const val GREEN_WIDTH_SALT = 0x67L
  }
}
