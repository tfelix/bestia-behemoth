package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.MacroCourse
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.NavigationService
import net.bestia.zone.travel.knowledge.Knower
import net.bestia.zone.travel.knowledge.RouteKnowledge
import org.springframework.stereotype.Component

/**
 * Chooses the course to a journey's current waypoint, avoiding what its knower believes is blocked, and says
 * what to walk at next along it.
 */
@Component
class RoutePlanner(
  private val navigation: NavigationService,
  private val knowledge: RouteKnowledge,
  private val navigationConfig: NavigationConfig,
) {

  /** No plan yet, a new waypoint, or the knower learned something since it planned. */
  fun needsPlan(journey: Journey, knower: Knower): Boolean {
    val plan = journey.plan ?: return true
    return plan.target != journey.target || plan.knowledgeRevision != knowledge.revisionOf(knower)
  }

  /** A plan from [from], or null when the knower knows no way there. */
  fun plan(from: Vec3L, journey: Journey, knower: Knower): TravelPlan? {
    val course = if (isCloseBy(from, journey.target)) {
      DIRECT
    } else {
      navigation.planCourse(from, journey.target, profileId = null) { knowledge.believesBlocked(knower, it) }
        ?: return null
    }

    return TravelPlan(journey.target, course, knowledge.revisionOf(knower))
  }

  /** The macro node to walk at, passing every one already reached, or the waypoint itself after the last. */
  fun nextAim(journey: Journey, position: Vec3L): Vec3L {
    val plan = journey.plan ?: return journey.target

    while (true) {
      val node = plan.currentNode ?: return journey.target
      val at = navigation.nodePosition(node)
      if (!isWithin(position, at, NODE_REACH)) return at

      plan.passNode()
      journey.progress.reachedAim()
      journey.route = outline(journey)
    }
  }

  /** The way still ahead, coarsely: each road bend and junction, then every waypoint still to visit. */
  fun outline(journey: Journey): List<Vec3L> {
    val points = ArrayList<Vec3L>()
    val plan = journey.plan

    if (plan != null) {
      for ((node, edge) in plan.remainingNodes.zip(plan.remainingEdges)) {
        points.addAll(navigation.bendsTowards(edge, node))
        points.add(navigation.nodePosition(node))
      }
    }

    points.addAll(journey.waypoints.drop(journey.nextIndex))
    return points
  }

  /** Near enough that walking to a road junction first would be a detour. */
  private fun isCloseBy(from: Vec3L, to: Vec3L): Boolean {
    return isWithin(from, to, navigationConfig.localSearchSpan * 2)
  }

  private fun isWithin(a: Vec3L, b: Vec3L, tiles: Long): Boolean {
    return Math.abs(a.x - b.x) <= tiles && Math.abs(a.y - b.y) <= tiles
  }

  private companion object {
    val DIRECT = MacroCourse(emptyList(), emptyList())

    /** A node stands on a road or a bridgehead, which may be occupied or gone; close is reached. */
    const val NODE_REACH = 4L
  }
}
