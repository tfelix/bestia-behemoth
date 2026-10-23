package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.MacroCourse

/** The course a traveller follows to its current waypoint, and what its knower believed when it chose it. */
class TravelPlan(
  val target: Vec3L,
  private val course: MacroCourse,
  val knowledgeRevision: Long,
) {

  private var passed = 0

  /** The macro node walked towards, or null once only the last stretch to [target] is left. */
  val currentNode: Int?
    get() = course.nodes.getOrNull(passed)

  /** The edge being walked, which is what is blamed when the traveller cannot get on. */
  val currentEdge: Int?
    get() = course.edges.getOrNull(passed)

  val remainingNodes: List<Int>
    get() = course.nodes.drop(passed)

  val remainingEdges: List<Int>
    get() = course.edges.drop(passed)

  fun passNode() {
    passed++
  }
}
