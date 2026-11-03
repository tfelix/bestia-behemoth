package net.bestia.zone.ai.ecs

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.travel.JourneyOrder
import net.bestia.zone.travel.JourneyService
import net.bestia.zone.util.EntityId
import kotlin.math.roundToLong
import kotlin.math.sqrt
import org.springframework.stereotype.Component as SpringComponent

/**
 * Walks every FEARED entity away from whoever frightened it. A player's journey order, so a creature's AI waits
 * it out instead of turning back to the fight, and the feared player's own move requests are refused meanwhile.
 */
@SpringComponent
class FearSystem(
  private val journeys: JourneyService,
) : System {
  override val phase = Phase.AI
  // After the AI acted, so its own step this tick does not undo the flight.
  override val after = setOf(AiActSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val reads: ComponentClassSet = setOf(StatusEffects::class, Position::class)

  override fun update(world: World, deltaTime: Float) {
    val flights = mutableListOf<Pair<EntityId, Vec3L>>()

    world.query(StatusEffects::class, Position::class).each { id ->
      val fear = get<StatusEffects>().activeEffects.firstOrNull { it.definitionId == StatusEffectId.FEARED.id }
        ?: return@each
      val source = fear.sourceEntityId?.let { world.get(it, Position::class)?.toVec3L() } ?: return@each

      flights.add(id to awayFrom(get<Position>().toVec3L(), source))
    }

    // Deferred, because starting a journey adds components this system does not own.
    if (flights.isNotEmpty()) {
      world.defer { flights.forEach { (id, to) -> journeys.start(world, id, listOf(to), JourneyOrder.PLAYER) } }
    }
  }

  private fun awayFrom(at: Vec3L, source: Vec3L): Vec3L {
    val dx = (at.x - source.x).toDouble()
    val dy = (at.y - source.y).toDouble()
    val length = sqrt(dx * dx + dy * dy)
    // Standing on the source gives no direction, so it runs along x.
    val (ux, uy) = if (length < 1e-6) 1.0 to 0.0 else dx / length to dy / length

    return Vec3L(at.x + (ux * FLEE_TILES).roundToLong(), at.y + (uy * FLEE_TILES).roundToLong(), at.z)
  }

  private companion object {
    // Far enough that a fast walker is still running when the next beat picks a new spot.
    const val FLEE_TILES = 6.0
  }
}
