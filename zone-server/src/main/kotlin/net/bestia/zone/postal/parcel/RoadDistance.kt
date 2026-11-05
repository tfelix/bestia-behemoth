package net.bestia.zone.postal.parcel

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.navigation.NavigationService
import net.bestia.zone.navigation.graph.MacroGraphService
import net.bestia.zone.postal.office.PostOffices
import org.springframework.stereotype.Service

/**
 * How far the coach drives between two post offices, in kilometres. Along the roads where it can; every metre off
 * them counts double, as the postal page of the design docs says. A road broken now is counted round.
 */
@Service
class RoadDistance(
  private val world: WorldView,
  private val offices: PostOffices,
  private val navigation: NavigationService,
  private val macroGraph: MacroGraphService,
) {

  /** Null when either office does not exist. Safe off the tick: the graph is read on a lease. */
  fun km(fromOffice: Int, toOffice: Int): Double? {
    return world.read {
      val from = offices.doorOf(fromOffice) ?: return@read null
      val to = offices.doorOf(toOffice) ?: return@read null
      if (fromOffice == toOffice) return@read 0.0

      val course = navigation.planCourse(from, to, COACH_PROFILE, isBlocked = macroGraph::isBlocked)
      if (course == null || course.edges.isEmpty()) {
        return@read OFF_ROAD_FACTOR * from.distance(to) / METRES_PER_KM
      }

      val graph = macroGraph.graph
      val edges = course.edges.map { graph.edges[it] }
      val onGraph = edges.sumOf { if (it.isMadeSurface) it.lengthMetres else OFF_ROAD_FACTOR * it.lengthMetres }

      // The legs onto and off the graph are open country. A course lists the nodes each edge reaches, so the
      // first node is the far end of the first edge's.
      val firstNode = edges.first().other(course.nodes.first())
      val entry = from.distance(graph.nodePositions[firstNode])
      val exit = graph.nodePositions[course.nodes.last()].distance(to)

      (onGraph + OFF_ROAD_FACTOR * (entry + exit)) / METRES_PER_KM
    }
  }

  private companion object {
    /** `movement/merchant-cart.yml`: roads or nothing, and turned back by a footbridge. */
    const val COACH_PROFILE = "merchant_cart"

    const val OFF_ROAD_FACTOR = 2.0
    const val METRES_PER_KM = 1000.0
  }
}
