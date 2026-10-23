package net.bestia.zone.travel

import net.bestia.zone.battle.ecs.attack.AttackCancelService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.anchor.AnchorPriority
import net.bestia.zone.world.anchor.SimulationAnchor
import net.bestia.zone.world.vicinity.Vicinity
import org.springframework.stereotype.Service

/** Starts and cancels journeys, for every one who may order one: the game, the companion app and the AI. */
@Service
class JourneyService(
  private val config: TravelConfig,
  private val attackCancel: AttackCancelService,
) {

  /**
   * Sends [entity] through [waypoints], replacing any journey it was on.
   *
   * @param leaseExpiresAt for an AI's journey, the tick it lapses at unless renewed; see [Journey.renewLease]
   * @return why it cannot go, or null when it set off
   */
  fun start(
    world: World,
    entity: EntityId,
    waypoints: List<Vec3L>,
    order: JourneyOrder,
    name: String? = null,
    savedRouteId: Long? = null,
    leaseExpiresAt: Long? = null,
  ): JourneyRefusal? {
    if (waypoints.isEmpty()) return JourneyRefusal.NO_DESTINATION
    if (waypoints.size > config.maxWaypoints) return JourneyRefusal.TOO_MANY_WAYPOINTS
    if (!world.has(entity, Position::class)) return JourneyRefusal.NOT_IN_WORLD
    if (world.has(entity, Dead::class)) return JourneyRefusal.DEAD

    if (order == JourneyOrder.PLAYER) attackCancel.cancelAttack(world, entity)

    world.add(entity, Journey(waypoints, order, name, savedRouteId, leaseExpiresAt))
    if (!world.has(entity, Vicinity::class)) world.add(entity, Vicinity())
    world.get(entity, SimulationAnchor::class)?.priority = AnchorPriority.TRAVELLER

    return null
  }

  /**
   * Adds [waypoints] after the destination of the journey [entity] is on; past [TravelConfig.maxWaypoints] they
   * are dropped.
   *
   * @return false when it is on no journey, which a caller answers by starting one
   */
  fun append(world: World, entity: EntityId, waypoints: List<Vec3L>): Boolean {
    val journey = world.get(entity, Journey::class) ?: return false

    val room = config.maxWaypoints - journey.waypoints.size
    if (room > 0) journey.append(waypoints.take(room))
    return true
  }

  /** Stops [entity] where it stands. */
  fun cancel(world: World, entity: EntityId) {
    if (!world.has(entity, Journey::class)) return

    world.remove(entity, Journey::class)
    world.remove(entity, Path::class)
    world.get(entity, SimulationAnchor::class)?.priority = AnchorPriority.RESTING
  }

  companion object {
    /** What [start] and [cancel] write, for a system that calls them from inside the tick. */
    val WRITES: ComponentClassSet = setOf(Journey::class, Path::class, Vicinity::class, SimulationAnchor::class)
  }
}
