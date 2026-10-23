package net.bestia.zone.ai.bt.leaves

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.travel.Journey
import net.bestia.zone.travel.JourneyOrder
import net.bestia.zone.travel.JourneyService

/**
 * Travels to [destination] however far it is, through the same journeys a player orders.
 *
 * RUNNING while the journey is under way, SUCCESS once it arrived and FAILURE when it ended anywhere else.
 * Each tick renews the journey's lease; a plan that moves on stops ticking this, and the journey lapses. The
 * journey only exists from the next tick on, because components added while the AI acts are added at the end.
 */
class TravelTo(
  private val destination: Vec3L,
  private val journeys: JourneyService,
) : BtNode {

  private var started = false

  override fun tick(context: BtContext): Status {
    val world = context.world
    val id = context.entityId

    if (!started) {
      val refusal = journeys.start(world, id, listOf(destination), JourneyOrder.AI, leaseExpiresAt = leaseFrom(context))
      started = refusal == null
      return if (started) Status.RUNNING else Status.FAILURE
    }

    val journey = world.get(id, Journey::class) ?: return if (hasArrived(context)) Status.SUCCESS else Status.FAILURE

    journey.renewLease(leaseFrom(context))
    return Status.RUNNING
  }

  private fun leaseFrom(context: BtContext): Long {
    return context.currentTick + LEASE_SECONDS * context.tickRate
  }

  private fun hasArrived(context: BtContext): Boolean {
    val position = context.world.get(context.entityId, Position::class)?.toVec3L() ?: return false
    return Math.abs(position.x - destination.x) <= 1 && Math.abs(position.y - destination.y) <= 1
  }

  override fun toString(): String {
    return "TravelTo($destination)"
  }

  private companion object {
    /** Longer than the slowest act cadence a creature can be throttled to. */
    const val LEASE_SECONDS = 5
  }
}
