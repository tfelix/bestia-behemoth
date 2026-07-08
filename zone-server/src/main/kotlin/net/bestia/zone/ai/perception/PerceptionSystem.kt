package net.bestia.zone.ai.perception

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.ecs.Brain
import net.bestia.zone.ai.memory.MemoryEntry
import net.bestia.zone.ai.memory.MemoryScope
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.player.Master
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.Ecs2System
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

/**
 * First stage of the AI pipeline. For every brain-equipped entity it queries the area-of-interest
 * service, snapshots nearby entities, and writes the result plus any enemy memory/events onto the
 * [Brain]. Think and act then work purely off this snapshot.
 *
 * Runs periodically (~0.5s); this is the perception refresh rate for all NPCs.
 */
@SpringComponent
@Order(10)
class PerceptionSystem(
  private val profileRegistry: AiProfileRegistry,
  private val aoiService: EntityAOIService
) : Ecs2System {

  override val schedule: Schedule = Schedule.EverySeconds(0.5f)
  override val reads: Set<KClass<out Component>> = setOf(Position::class, Health::class, Master::class, Brain::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Brain::class, Position::class).each { id ->
      val brain = get<Brain>()
      val position = get<Position>()

      val profile = profileRegistry.get(brain.profileId) ?: return@each
      val selfPos = position.toVec3L()
      val now = System.currentTimeMillis()

      val sightSize = profile.perception.sightRadius.toLong() * 2
      val neighbourIds = aoiService.queryEntitiesInCube(selfPos, sightSize)
        .filter { it != id }

      val percepts = neighbourIds.mapNotNull { neighbourId ->
        val pos = world.get(neighbourId, Position::class)?.toVec3L()
          ?: return@mapNotNull null

        Percept(
          entityId = neighbourId,
          position = pos,
          hostile = world.has(neighbourId, Master::class),
          healthPct = healthPct(world, neighbourId)
        )
      }

      brain.latestPercept = PerceptionSnapshot(selfPos, percepts, now)

      val nearestHostile = percepts
        .filter { it.hostile }
        .minByOrNull { selfPos.distance(it.position) }

      if (nearestHostile != null) {
        brain.targetId = nearestHostile.entityId
        brain.targetPosition = nearestHostile.position
        brain.threatPosition = nearestHostile.position
        brain.memory.remember(
          MemoryEntry(
            key = "enemy:${nearestHostile.entityId}",
            type = MemoryEntry.MemoryType.ENEMY_SIGHTING,
            position = nearestHostile.position,
            entityId = nearestHostile.entityId,
            timestampMs = now,
            confidence = 1.0,
            expiresAtMs = now + ENEMY_MEMORY_TTL_MS,
            scope = MemoryScope.INDIVIDUAL
          )
        )
        brain.pushEvent(AiEvent(AiEventType.ENEMY_SEEN, nearestHostile.entityId, now))
      } else {
        if (brain.targetId != null) {
          brain.pushEvent(AiEvent(AiEventType.ENEMY_LOST, brain.targetId, now))
        }
        brain.targetId = null
        brain.targetPosition = null
        brain.threatPosition = null
      }

      brain.memory.forgetExpired(now)
    }
  }

  private fun healthPct(world: World, entityId: EntityId): Double {
    val health = world.get(entityId, Health::class) ?: return 1.0
    return if (health.max > 0) health.current.toDouble() / health.max else 0.0
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val ENEMY_MEMORY_TTL_MS = 5_000L
  }
}
