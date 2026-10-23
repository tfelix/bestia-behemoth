package net.bestia.zone.travel

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L

/**
 * An order to travel through [waypoints] in turn; the last one is the destination.
 *
 * Whoever gave it - a player, the companion app or a creature's own AI - [TravelSystem] carries it out the same
 * way: it plans a course with what the traveller knows, walks it leg by leg, and ends it as arrived or failed.
 */
class Journey(
  waypoints: List<Vec3L>,
  val order: JourneyOrder,
  val name: String? = null,
  val savedRouteId: Long? = null,
  leaseExpiresAt: Long? = null,
) : Component {

  val waypoints: List<Vec3L> = waypoints.toList()

  init {
    require(waypoints.isNotEmpty()) { "A journey needs a destination" }
  }

  var nextIndex = 0
    private set

  /** The waypoint being travelled to now. */
  val target: Vec3L
    get() = waypoints[nextIndex]

  val isOnLastLeg: Boolean
    get() = nextIndex == waypoints.lastIndex

  /**
   * The tick an [JourneyOrder.AI] journey is dropped at unless its AI renews it, which is how a creature that
   * turned to something else stops travelling without having to say so. Null for a player's order.
   */
  var leaseExpiresAt: Long? = leaseExpiresAt
    private set

  fun renewLease(untilTick: Long) {
    leaseExpiresAt = untilTick
  }

  fun isAbandonedAt(tick: Long): Boolean {
    return leaseExpiresAt?.let { tick > it } ?: false
  }

  /** Working state of [TravelSystem]; nothing else reads it. */
  var plan: TravelPlan? = null
  val progress = TravelProgress()
  var secondsSinceRefined = Float.MAX_VALUE

  fun advanceToNextWaypoint() {
    check(!isOnLastLeg) { "The destination has no waypoint after it" }
    nextIndex++
    plan = null
    progress.reachedAim()
  }
}
