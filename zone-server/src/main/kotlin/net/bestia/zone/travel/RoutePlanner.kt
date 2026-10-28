package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.MacroCourse
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.NavigationService
import net.bestia.zone.navigation.region.RegionGrid
import net.bestia.zone.navigation.region.WaySearches
import net.bestia.zone.travel.TravelPlan.Stop
import net.bestia.zone.travel.knowledge.Knower
import net.bestia.zone.travel.knowledge.RouteKnowledge
import org.springframework.stereotype.Component

/**
 * Chooses the way to a journey's current waypoint, avoiding what its knower believes is blocked, and says what
 * to walk at next along it.
 *
 * The way is laid over the [RegionGrid], which knows the ground: it walks round water, keeps to valleys rather
 * than climbing over ridges, and takes a road where one runs the right way. A bridge the knower believes has
 * fallen is kept clear of, so the way goes round by another.
 *
 * The macro graph's nodes stand kilometres apart, too far apart to walk by, so it is only asked which way to head
 * for a waypoint further than one search of the grid spans: the way is then laid a stretch at a time, to the
 * furthest node of the graph's course within reach, and the next stretch is laid as the traveller nears the end
 * of this one. Where the grid finds no way at all - a waypoint across a strait, a bridge further off than its
 * search reaches - the graph's course is walked node by node.
 */
@Component
class RoutePlanner(
  private val navigation: NavigationService,
  private val knowledge: RouteKnowledge,
  private val navigationConfig: NavigationConfig,
  private val ways: WaySearches,
) {

  /** What [plan] came to. */
  sealed interface Planned {
    class Ready(val plan: TravelPlan) : Planned

    /** The knower knows no way there, or water cuts the waypoint off. */
    data object NoRoute : Planned

    /** The way over the ground is still being searched for, off the tick. Ask again next tick. */
    data object NotYet : Planned
  }

  /** A way over the ground, as [overGround] found it. */
  private sealed interface Leg {
    class Corners(val points: List<Vec3L>) : Leg
    data object CutOff : Leg
    data object Unknown : Leg
    data object Surveying : Leg
  }

  /** No plan yet, a new waypoint, the knower learned something since it planned, or a stretch nearly walked. */
  fun needsPlan(journey: Journey, knower: Knower, position: Vec3L): Boolean {
    val plan = journey.plan ?: return true
    if (plan.target != journey.target || plan.knowledgeRevision != knowledge.revisionOf(knower)) return true

    val end = plan.end ?: return false
    return plan.isStretch && distance(position, end) < NEXT_STRETCH_METRES
  }

  /** Whether the traveller may walk on along its plan while the next stretch is searched for. */
  fun mayWalkOnWhileSurveying(journey: Journey, knower: Knower): Boolean {
    return stretchBeingWalked(journey, knower)?.current != null
  }

  /**
   * A plan from [from] to the journey's current waypoint.
   *
   * A stretch nearly walked is carried on from where it ends rather than from where the traveller is, which
   * keeps the next stretch the same search however far the traveller walks while it is being made.
   */
  fun plan(from: Vec3L, journey: Journey, knower: Knower): Planned {
    val walking = stretchBeingWalked(journey, knower) ?: return planFrom(from, journey, knower)
    val end = walking.end ?: return planFrom(from, journey, knower)

    val next = when (val planned = planFrom(end, journey, knower)) {
      is Planned.Ready -> planned
      else -> return planned
    }
    val plan = TravelPlan(
      journey.target, walking.remaining + next.plan.remaining, next.plan.knowledgeRevision,
      next.plan.isStretch, next.plan.beyond,
    )
    return Planned.Ready(plan)
  }

  /** The journey's plan, if it is a stretch that still holds: the same waypoint, nothing learned since. */
  private fun stretchBeingWalked(journey: Journey, knower: Knower): TravelPlan? {
    val plan = journey.plan ?: return null
    val holds = plan.target == journey.target && plan.knowledgeRevision == knowledge.revisionOf(knower)
    return plan.takeIf { holds && it.isStretch }
  }

  private fun planFrom(from: Vec3L, journey: Journey, knower: Knower): Planned {
    val target = journey.target
    if (isCloseBy(from, target)) return ready(journey, knower, emptyList())

    val isBlocked = { edge: Int -> knowledge.believesBlocked(knower, edge) }
    val avoid = navigation.blockedBridgeSpans(isBlocked)

    val direct = if (isWithin(from, target, SEARCH_SPAN)) overGround(from, target, avoid) else null
    when (direct) {
      is Leg.Corners -> return ready(journey, knower, stopsOver(from, direct.points, keepLast = false))
      Leg.Surveying -> return Planned.NotYet
      Leg.CutOff, Leg.Unknown, null -> Unit
    }

    // Several ways onto the graph only where the grid cannot be asked: a straight line to one is blind to water.
    val entries = if (direct == null) COURSE_ENTRIES else 1
    val course = navigation.planCourse(from, target, profileId = null, entries, isBlocked)
    if (course == null || course.nodes.isEmpty()) {
      return when (val way = direct ?: overGround(from, target, avoid)) {
        is Leg.Corners -> ready(journey, knower, stopsOver(from, way.points, keepLast = false))
        Leg.Surveying -> Planned.NotYet
        Leg.CutOff -> Planned.NoRoute
        Leg.Unknown -> if (course == null) Planned.NoRoute else ready(journey, knower, emptyList())
      }
    }

    if (direct == null) stretchAlong(from, course, journey, knower, avoid)?.let { return it }

    return alongCourse(from, course, journey, knower, avoid)
  }

  /**
   * The next stretch towards a waypoint beyond one search's reach: over the ground to the furthest node of
   * [course] within [SEARCH_SPAN], never one nearer than twice [NEXT_STRETCH_METRES], which would only be
   * planned again on the spot. Null when the grid finds no way to it.
   */
  private fun stretchAlong(
    from: Vec3L,
    course: MacroCourse,
    journey: Journey,
    knower: Knower,
    avoid: List<Pair<Vec3L, Vec3L>>,
  ): Planned? {
    val positions = course.nodes.map(navigation::nodePosition)
    val firstOutOfReach = positions.indexOfFirst { !isWithin(from, it, SEARCH_SPAN) }
    var end = if (firstOutOfReach < 0) positions.lastIndex else maxOf(firstOutOfReach - 1, 0)
    while (end < positions.lastIndex && distance(from, positions[end]) < NEXT_STRETCH_METRES * 2) end++

    val node = positions[end]
    return when (val way = overGround(from, node, avoid)) {
      is Leg.Corners -> {
        val corners = stopsOver(from, way.points.dropLast(1), keepLast = true)
        val lastCorner = way.points.getOrNull(way.points.lastIndex - 1) ?: from
        val arrival = Stop(node, navigation.bridgeCrossedBy(lastCorner, node), NODE_REACH)
        val plan = TravelPlan(
          journey.target, corners + arrival, knowledge.revisionOf(knower),
          isStretch = true, beyond = positions.drop(end + 1),
        )
        Planned.Ready(plan)
      }
      Leg.Surveying -> Planned.NotYet
      Leg.CutOff, Leg.Unknown -> null
    }
  }

  /** The graph's course stop by stop, with the ways onto it and off it to the waypoint laid over the ground. */
  private fun alongCourse(
    from: Vec3L,
    course: MacroCourse,
    journey: Journey,
    knower: Knower,
    avoid: List<Pair<Vec3L, Vec3L>>,
  ): Planned {
    val onCourse = ArrayList<Stop>()
    for ((node, edge) in course.nodes.zip(course.edges)) {
      navigation.bendsTowards(edge, node).mapTo(onCourse) { Stop(it, edge, BEND_REACH) }
      onCourse.add(Stop(navigation.nodePosition(node), edge, NODE_REACH))
    }

    val first = onCourse.first().position
    val last = onCourse.last().position
    val legs = listOf(overGround(from, first, avoid), overGround(last, journey.target, avoid))
    if (legs.any { it == Leg.CutOff }) return Planned.NoRoute
    if (legs.any { it == Leg.Surveying }) return Planned.NotYet

    // A leg the grid cannot tell about is walked straight.
    val (onto, offTo) = legs.map { (it as? Leg.Corners)?.points.orEmpty() }
    val stops = stopsOver(from, onto, keepLast = false) + onCourse + stopsOver(last, offTo, keepLast = false)
    return ready(journey, knower, stops)
  }

  /**
   * The way over the ground from [from] to [to], its points ending on [to]; straight when the grid cannot tell
   * at all, as when no world is loaded.
   */
  private fun overGround(from: Vec3L, to: Vec3L, avoid: List<Pair<Vec3L, Vec3L>>): Leg {
    if (isCloseBy(from, to)) return Leg.Corners(listOf(to))

    return when (val way = ways.way(from, to, avoid)) {
      is RegionGrid.Way.Found -> Leg.Corners(way.points)
      RegionGrid.Way.None -> Leg.CutOff
      RegionGrid.Way.Unknown -> Leg.Unknown
      RegionGrid.Way.Pending -> Leg.Surveying
    }
  }

  /**
   * [points] walked from [from] as stops, each blaming the bridge the way to it crosses, if any. The last point
   * is left out unless [keepLast] - it is the waypoint, which is walked to after the stops - but kept after all
   * when a bridge leads to it, so that a traveller stuck there knows what to blame.
   */
  private fun stopsOver(from: Vec3L, points: List<Vec3L>, keepLast: Boolean): List<Stop> {
    val stops = ArrayList<Stop>(points.size)
    var previous = from
    for ((i, point) in points.withIndex()) {
      val bridge = navigation.bridgeCrossedBy(previous, point)
      if (keepLast || i < points.lastIndex || bridge != null) stops.add(Stop(point, bridge, CORNER_REACH))
      previous = point
    }
    return stops
  }

  private fun ready(journey: Journey, knower: Knower, stops: List<Stop>): Planned {
    return Planned.Ready(TravelPlan(journey.target, stops, knowledge.revisionOf(knower)))
  }

  private fun distance(a: Vec3L, b: Vec3L): Double {
    return Math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
  }

  /** The stop to walk at, passing every one already reached, or the waypoint itself after the last. */
  fun nextAim(journey: Journey, position: Vec3L): Vec3L {
    val plan = journey.plan ?: return journey.target

    while (true) {
      val stop = plan.current ?: return journey.target
      if (!isWithin(position, stop.position, stop.reach)) return stop.position

      plan.pass()
      journey.progress.reachedAim()
      journey.route = outline(journey)
    }
  }

  /**
   * The way still ahead, coarsely: each corner, road bend and junction, the graph's course past the stretch
   * being walked, then every waypoint still to visit.
   */
  fun outline(journey: Journey): List<Vec3L> {
    val plan = journey.plan
    val ahead = plan?.remaining.orEmpty().map { it.position } + plan?.beyond.orEmpty()
    return ahead + journey.waypoints.drop(journey.nextIndex)
  }

  /** Near enough that the local tier finds the way by itself. */
  private fun isCloseBy(from: Vec3L, to: Vec3L): Boolean {
    return isWithin(from, to, navigationConfig.localSearchSpan * 2)
  }

  private fun isWithin(a: Vec3L, b: Vec3L, tiles: Long): Boolean {
    return Math.abs(a.x - b.x) <= tiles && Math.abs(a.y - b.y) <= tiles
  }

  private companion object {
    /** A node stands on a road or a bridgehead, which may be occupied or gone; close is reached. */
    const val NODE_REACH = 4L

    const val BEND_REACH = 4L

    /** A corner is the middle of a cell the way crosses; a quarter of one is near enough. */
    const val CORNER_REACH = 8L

    /**
     * The farthest apart, in either axis, the ends of one search of the grid may be. A search this long in new
     * country surveys a hundred-odd kilometre blocks, some three seconds the traveller stands waiting for its
     * first stretch; every stretch after is laid while it walks the one before.
     */
    const val SEARCH_SPAN = 16_000L

    /** How many nodes nearest each end a long way's course may enter or leave the graph by. */
    const val COURSE_ENTRIES = 4

    /** How near the end of a stretch the next one is laid, so the traveller never stops to wait for it. */
    const val NEXT_STRETCH_METRES = 1_500.0
  }
}
