package net.bestia.zone.navigation

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L

/**
 * A long journey in progress: the macro nodes still to visit, and where it is ultimately going.
 *
 * ### The whole route is never turned into steps up front
 *
 * Only [nextLeg] is, and only when the entity has nearly walked the last one. A route across a continent is
 * hundreds of kilometres; refining it into columns in advance would cost memory proportional to distance
 * travelled-in-future, force the ground to be loaded far from any player, and go stale before it was used.
 * Refining as you arrive costs the same total work spread over the journey and only ever touches ground
 * somebody is near.
 *
 * ### Why the graph version is here
 *
 * So that a route planned before a bridge fell can be recognised as stale without being replanned on the
 * instant. See [replanAfterTick] - the delay is what makes the population learn the news gradually rather than
 * all together.
 */
data class MacroRoute(
  /** Node ids still ahead, in order. The one being walked towards is first. */
  private var remaining: MutableList<Int>,
  val destination: Vec3L,
  /** [net.bestia.zone.navigation.graph.MacroGraphService.version] this was planned against. */
  var graphVersion: Long,
  /**
   * Tick before which this route is not replanned, even knowing it is stale.
   *
   * Zero means "no replan scheduled". Set once, to a random point inside the configured jitter, the first time
   * the route is noticed to be out of date.
   */
  var replanAfterTick: Long = 0L
) : Component {

  val nodesRemaining: Int get() = remaining.size

  val isFinished: Boolean get() = remaining.isEmpty()

  /** The node currently being walked towards, or null when the macro part of the journey is done. */
  fun currentNode(): Int? = remaining.firstOrNull()

  fun advance() {
    if (remaining.isNotEmpty()) remaining.removeFirst()
  }

  fun nodes(): List<Int> = remaining.toList()

  fun replaceWith(nodes: List<Int>, version: Long) {
    remaining = nodes.toMutableList()
    graphVersion = version
    replanAfterTick = 0L
  }
}
