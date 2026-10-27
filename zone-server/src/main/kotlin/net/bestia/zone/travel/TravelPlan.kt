package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L

/** The way a traveller follows to its current waypoint, and what its knower believed when it chose it. */
class TravelPlan(
  val target: Vec3L,
  private val stops: List<Stop>,
  val knowledgeRevision: Long,
) {

  /**
   * One place to walk at on the way: a corner round water, a bend of a road, or a node of the macro graph.
   *
   * @property edge the macro edge this stop lies on, which is what is blamed when the traveller cannot get on;
   *   null off the graph, where there is nothing to learn is blocked
   * @property reach how near counts as there. A node stands on a road or a bridgehead, which may be occupied or
   *   gone; a corner round water is only the middle of a cell that is mostly dry.
   */
  class Stop(val position: Vec3L, val edge: Int?, val reach: Long)

  private var passed = 0

  /** The stop walked towards, or null once only the last stretch to [target] is left. */
  val current: Stop?
    get() = stops.getOrNull(passed)

  /** The edge being walked, which is what is blamed when the traveller cannot get on. */
  val currentEdge: Int?
    get() = current?.edge

  val remaining: List<Stop>
    get() = stops.drop(passed)

  fun pass() {
    passed++
  }
}
