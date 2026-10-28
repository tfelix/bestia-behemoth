package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.MacroCourse
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.NavigationService
import net.bestia.zone.navigation.region.RegionGrid
import net.bestia.zone.travel.TravelPlan.Stop
import net.bestia.zone.travel.knowledge.Knower
import net.bestia.zone.travel.knowledge.RouteKnowledge
import org.springframework.stereotype.Component

/**
 * Chooses the way to a journey's current waypoint, avoiding what its knower believes is blocked, and says what
 * to walk at next along it.
 *
 * The macro graph offers a course, since it is what knows which bridge has fallen. The [RegionGrid] lays the
 * stretches the graph leaves out - onto it, off it to the waypoint, or the whole way when the graph has nothing
 * to offer - round any water in between.
 *
 * The graph's nodes stand kilometres apart, so the nearest one to either end can be far behind it: a course
 * between two of them can lead a traveller a kilometre into town and back for a walk of three hundred metres.
 * Where the course wanders, the grid's way straight there is weighed against it.
 */
@Component
class RoutePlanner(
  private val navigation: NavigationService,
  private val knowledge: RouteKnowledge,
  private val navigationConfig: NavigationConfig,
  private val grid: RegionGrid,
  private val budget: TravelBudget,
) {

  /** What [plan] came to. */
  sealed interface Planned {
    class Ready(val plan: TravelPlan) : Planned

    /** The knower knows no way there, or water cuts the waypoint off. */
    data object NoRoute : Planned

    /** The way runs through country the grid has not surveyed yet. Ask again next tick. */
    data object NotYet : Planned
  }

  /** A stretch off the graph, as [overGround] found it. */
  private sealed interface Leg {
    class Corners(val stops: List<Stop>) : Leg
    data object CutOff : Leg
    data object Surveying : Leg
  }

  /** No plan yet, a new waypoint, or the knower learned something since it planned. */
  fun needsPlan(journey: Journey, knower: Knower): Boolean {
    val plan = journey.plan ?: return true
    return plan.target != journey.target || plan.knowledgeRevision != knowledge.revisionOf(knower)
  }

  /** A plan from [from] to the journey's current waypoint. */
  fun plan(from: Vec3L, journey: Journey, knower: Knower): Planned {
    val target = journey.target
    if (isCloseBy(from, target)) return ready(journey, knower, emptyList())

    val isBlocked = { edge: Int -> knowledge.believesBlocked(knower, edge) }
    val course = navigation.planCourse(from, target, profileId = null, isBlocked)
    if (course != null && course.nodes.isEmpty()) {
      return when (val leg = overGround(from, target)) {
        is Leg.Corners -> ready(journey, knower, leg.stops)
        Leg.CutOff -> Planned.NoRoute
        Leg.Surveying -> Planned.NotYet
      }
    }

    val viaGraph = when (val leg = course?.let { alongCourse(from, target, it) }) {
      is Leg.Corners -> leg.stops
      Leg.Surveying -> return Planned.NotYet
      Leg.CutOff, null -> null
    }
    val isWorthBettering = viaGraph == null || lengthOf(from, viaGraph, target) > distance(from, target) * GRAPH_PREFERENCE
    if (!isWorthBettering || !isWithin(from, target, DIRECT_SEARCH_SPAN)) {
      return viaGraph?.let { ready(journey, knower, it) } ?: Planned.NoRoute
    }

    val direct = when (val way = grid.wayBetween(from, target, budget.surveys)) {
      is RegionGrid.Way.Found -> way.points.takeUnless { navigation.crossesBlockedBridge(listOf(from) + it, isBlocked) }
      RegionGrid.Way.Pending -> return Planned.NotYet
      RegionGrid.Way.None, RegionGrid.Way.Unknown -> null
    }?.let(::cornerStops)

    val stops = when {
      direct == null -> viaGraph ?: return Planned.NoRoute
      viaGraph == null -> direct
      lengthOf(from, viaGraph, target) > lengthOf(from, direct, target) * GRAPH_PREFERENCE -> direct
      else -> viaGraph
    }
    return ready(journey, knower, stops)
  }

  /** The course's own stops, with the ways onto it and off it to [target] round water. */
  private fun alongCourse(from: Vec3L, target: Vec3L, course: MacroCourse): Leg {
    val onCourse = ArrayList<Stop>()
    for ((node, edge) in course.nodes.zip(course.edges)) {
      navigation.bendsTowards(edge, node).mapTo(onCourse) { Stop(it, edge, BEND_REACH) }
      onCourse.add(Stop(navigation.nodePosition(node), edge, NODE_REACH))
    }

    val legs = listOf(overGround(from, onCourse.first().position), overGround(onCourse.last().position, target))
    if (legs.any { it == Leg.CutOff }) return Leg.CutOff
    if (legs.any { it == Leg.Surveying }) return Leg.Surveying

    val (onto, offTo) = legs.map { (it as Leg.Corners).stops }
    return Leg.Corners(onto + onCourse + offTo)
  }

  private fun ready(journey: Journey, knower: Knower, stops: List<Stop>): Planned {
    return Planned.Ready(TravelPlan(journey.target, stops, knowledge.revisionOf(knower)))
  }

  /** The corners round water from [from] to [to], [to] itself left out. */
  private fun overGround(from: Vec3L, to: Vec3L): Leg {
    if (isCloseBy(from, to)) return Leg.Corners(emptyList())

    return when (val way = grid.wayBetween(from, to, budget.surveys)) {
      is RegionGrid.Way.Found -> Leg.Corners(cornerStops(way.points))
      RegionGrid.Way.None -> Leg.CutOff
      RegionGrid.Way.Unknown -> Leg.Corners(emptyList())
      RegionGrid.Way.Pending -> Leg.Surveying
    }
  }

  /** A [RegionGrid.Way.Found]'s points as stops, the destination it ends on left out. */
  private fun cornerStops(points: List<Vec3L>): List<Stop> {
    return points.dropLast(1).map { Stop(it, edge = null, CORNER_REACH) }
  }

  /** How far it is from [from] by each of [stops] to [to]. */
  private fun lengthOf(from: Vec3L, stops: List<Stop>, to: Vec3L): Double {
    val points = listOf(from) + stops.map { it.position } + to
    return points.zipWithNext(::distance).sum()
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

  /** The way still ahead, coarsely: each corner, road bend and junction, then every waypoint still to visit. */
  fun outline(journey: Journey): List<Vec3L> {
    val ahead = journey.plan?.remaining.orEmpty().map { it.position }
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

    /** A corner is the middle of a cell that is only mostly dry; a quarter of one is near enough. */
    const val CORNER_REACH = 8L

    /**
     * How much longer than the way straight over the ground the graph's course may be and still be taken. Roads
     * are graded and bridged where open country may climb, so a course a little longer is the better walk.
     */
    const val GRAPH_PREFERENCE = 1.25

    /**
     * The farthest apart, in either axis, the ends may be for a way straight over the ground to be searched.
     * The box round ends this far apart holds a few hundred kilometre blocks, if the search reaches them all.
     */
    const val DIRECT_SEARCH_SPAN = 8_000L
  }
}
