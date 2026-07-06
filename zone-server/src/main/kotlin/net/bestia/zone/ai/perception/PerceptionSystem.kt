package net.bestia.zone.ai.perception

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.ecs.Brain
import net.bestia.zone.ai.memory.MemoryEntry
import net.bestia.zone.ai.memory.MemoryScope
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.PeriodicSystem
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.player.Master
import org.springframework.stereotype.Component

/**
 * First stage of the AI pipeline. For every brain-equipped entity it queries the area-of-interest
 * service, snapshots nearby entities under short foreign read locks, and writes the result plus any
 * enemy memory/events onto the [Brain]. Think and act then work purely off this snapshot, so the hot
 * path never takes a foreign read lock.
 *
 * Runs periodically (~0.5s); this is the perception refresh rate for all NPCs.
 */
@Component
class PerceptionSystem(
  private val profileRegistry: AiProfileRegistry,
  private val aoiService: EntityAOIService
) : PeriodicSystem(
  delay = 0.5f,
  setOf(Brain::class, Position::class)
) {

  override fun update(deltaTime: Float, entity: Entity, zone: ZoneServer) {
    val brain = entity.getOrThrow(Brain::class)
    val profile = profileRegistry.get(brain.profileId) ?: return
    val selfPos = entity.getOrThrow(Position::class).toVec3L()
    val now = System.currentTimeMillis()

    val sightSize = profile.perception.sightRadius.toLong() * 2
    val neighbourIds = aoiService.queryEntitiesInCube(selfPos, sightSize)
      .filter { it != entity.id }

    val percepts = neighbourIds.mapNotNull { neighbourId ->
      zone.withEntityReadLock(neighbourId) { neighbour ->
        val pos = neighbour.get(Position::class)?.toVec3L()
          ?: return@withEntityReadLock null

        Percept(
          entityId = neighbourId,
          position = pos,
          hostile = neighbour.has(Master::class),
          healthPct = healthPct(neighbour)
        )
      }
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

  private fun healthPct(entity: Entity): Double {
    val health = entity.get(Health::class) ?: return 1.0
    return if (health.max > 0) health.current.toDouble() / health.max else 0.0
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val ENEMY_MEMORY_TTL_MS = 5_000L
  }
}
