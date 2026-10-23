package net.bestia.zone.travel

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.movement.ecs.MoveSystem
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.spoor.ActorSignatureSystem
import net.bestia.zone.travel.knowledge.Knower
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.anchor.AnchorPriority
import net.bestia.zone.world.anchor.SimulationAnchor
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component as SpringComponent

/**
 * Carries every [Journey] one step further: reach a waypoint, plan the course, refine the next leg, and give up
 * when there is no way on. Journeys that waited longest for a search go first, so a busy tick is fair.
 */
@SpringComponent
class TravelSystem(
  private val planner: RoutePlanner,
  private val refiner: LegRefiner,
  private val watchdog: ProgressWatchdog,
  private val budget: TravelBudget,
  private val events: ApplicationEventPublisher,
) : System {

  override val phase = Phase.MOVEMENT

  /** A leg laid this tick is walked this tick, and signed before it is walked. */
  override val before = setOf(ActorSignatureSystem::class, MoveSystem::class)

  override val reads: ComponentClassSet = setOf(Position::class, Dead::class, Account::class) + Knower.READS

  override val writes: ComponentClassSet = setOf(Journey::class, Path::class, SimulationAnchor::class)

  override fun update(world: World, deltaTime: Float) {
    budget.refill()

    for ((id, journey) in journeysLongestWaitingFirst(world)) {
      journey.secondsSinceRefined += deltaTime
      travel(world, id, journey, deltaTime)
    }
  }

  private fun travel(world: World, id: EntityId, journey: Journey, deltaTime: Float) {
    if (journey.isAbandonedAt(world.tickCount)) return abandon(world, id)
    if (world.has(id, Dead::class)) return end(world, id, journey, JourneyFailure.DIED)

    val position = world.getOrThrow(id, Position::class).toVec3L()

    if (hasReached(position, journey.target)) {
      if (journey.isOnLastLeg) return end(world, id, journey, failure = null)
      journey.advanceToNextWaypoint()
    }

    val knower = Knower.of(world, id)
    if (planner.needsPlan(journey, knower)) {
      journey.plan = planner.plan(position, journey, knower) ?: return end(world, id, journey, JourneyFailure.NO_ROUTE)
      journey.route = planner.outline(journey)
    }

    val aim = planner.nextAim(journey, position)
    if (refiner.isDue(journey, world.get(id, Path::class), aim)) refiner.refineNextLeg(world, id, journey, position, aim)

    watchdog.check(journey, knower, position, aim, deltaTime)?.let { return end(world, id, journey, it) }
  }

  private fun journeysLongestWaitingFirst(world: World): List<Pair<EntityId, Journey>> {
    val journeys = world.query(Journey::class, Position::class).map { id -> id to get<Journey>() }
    return journeys.sortedByDescending { (_, journey) -> journey.secondsSinceRefined }
  }

  private fun hasReached(position: Vec3L, target: Vec3L): Boolean {
    return Math.abs(position.x - target.x) <= ARRIVAL_TILES && Math.abs(position.y - target.y) <= ARRIVAL_TILES
  }

  /** Its AI moved on; nobody is told, there is nobody who waits for it. */
  private fun abandon(world: World, id: EntityId) {
    world.remove(id, Journey::class)
    world.remove(id, Path::class)
  }

  private fun end(world: World, id: EntityId, journey: Journey, failure: JourneyFailure?) {
    world.remove(id, Journey::class)
    if (failure != null) world.remove(id, Path::class)
    world.get(id, SimulationAnchor::class)?.priority = AnchorPriority.RESTING

    val position = world.getOrThrow(id, Position::class).toVec3L()
    val accountId = world.get(id, Account::class)?.accountId
    events.publishEvent(JourneyEndedEvent(this, id, accountId, journey.order, journey.name, position, failure))
  }

  private companion object {
    const val ARRIVAL_TILES = 1L
  }
}
