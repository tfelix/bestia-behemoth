package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L
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
 * The macro graph decides the course, since it is what knows which bridge has fallen. The [RegionGrid] lays the
 * stretches the graph leaves out - onto it, off it to the waypoint, or the whole way when the graph has nothing
 * to offer - round any water in between.
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

    val course = navigation.planCourse(from, target, profileId = null) { knowledge.believesBlocked(knower, it) }
      ?: return Planned.NoRoute

    val onCourse = ArrayList<Stop>()
    for ((node, edge) in course.nodes.zip(course.edges)) {
      navigation.bendsTowards(edge, node).mapTo(onCourse) { Stop(it, edge, BEND_REACH) }
      onCourse.add(Stop(navigation.nodePosition(node), edge, NODE_REACH))
    }

    val legs = if (onCourse.isEmpty()) {
      listOf(overGround(from, target))
    } else {
      listOf(overGround(from, onCourse.first().position), overGround(onCourse.last().position, target))
    }
    if (legs.any { it == Leg.CutOff }) return Planned.NoRoute
    if (legs.any { it == Leg.Surveying }) return Planned.NotYet

    val corners = legs.map { (it as Leg.Corners).stops }
    val stops = if (onCourse.isEmpty()) corners[0] else corners[0] + onCourse + corners[1]
    return ready(journey, knower, stops)
  }

  private fun ready(journey: Journey, knower: Knower, stops: List<Stop>): Planned {
    return Planned.Ready(TravelPlan(journey.target, stops, knowledge.revisionOf(knower)))
  }

  /** The corners round water from [from] to [to], [to] itself left out. */
  private fun overGround(from: Vec3L, to: Vec3L): Leg {
    if (isCloseBy(from, to)) return Leg.Corners(emptyList())

    return when (val way = grid.wayBetween(from, to, budget.surveys)) {
      is RegionGrid.Way.Found -> Leg.Corners(way.points.dropLast(1).map { Stop(it, edge = null, CORNER_REACH) })
      RegionGrid.Way.None -> Leg.CutOff
      RegionGrid.Way.Unknown -> Leg.Corners(emptyList())
      RegionGrid.Way.Pending -> Leg.Surveying
    }
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
  }
}
