package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L

/**
 * The way a traveller follows to its current waypoint, and what its knower believed when it chose it.
 *
 * A waypoint further than one search of the ground spans is planned a stretch at a time: the plan then ends on
 * the macro graph's node it headed for, [isStretch] is set, and [beyond] is the rest of the graph's course, which
 * is what the owner's map shows of the way past it until the next stretch is laid.
 */
class TravelPlan(
  val target: Vec3L,
  private val stops: List<Stop>,
  val knowledgeRevision: Long,
  val isStretch: Boolean = false,
  val beyond: List<Vec3L> = emptyList(),
) {

  /**
   * One place to walk at on the way: a corner of a way over the ground, a bend of a road, or a node of the macro
   * graph.
   *
   * @property edge the macro edge this stop lies on or the bridge the way to it crosses, which is what is blamed
   *   when the traveller cannot get on; null in open country, where there is nothing to learn is blocked
   * @property reach how near counts as there. A node stands on a road or a bridgehead, which may be occupied or
   *   gone; a corner is only the middle of a cell.
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

  /** Where the plan ends: its last stop, or null when it walks straight to [target]. */
  val end: Vec3L?
    get() = stops.lastOrNull()?.position

  fun pass() {
    passed++
  }
}
