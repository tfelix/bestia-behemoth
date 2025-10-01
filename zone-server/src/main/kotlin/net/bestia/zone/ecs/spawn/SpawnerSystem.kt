package net.bestia.zone.ecs.spawn

import net.bestia.zone.bestia.BestiaEntityFactory
import net.bestia.zone.ecs2.Entity
import net.bestia.zone.ecs2.IteratingSystem
import net.bestia.zone.ecs2.ZoneServer
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class SpawnerSystem(
  private val bestiaEntityFactory: BestiaEntityFactory,
) : IteratingSystem() {
  override val requiredComponents = setOf(
    Spawner::class
  )

  override fun update(
    deltaTime: Float,
    entity: Entity,
    zone: ZoneServer
  ) {
    val spawner = entity.getOrThrow(Spawner::class)

    removeDeadEntities(spawner, zone)

    spawnMissingEntities(spawner, zone)
  }

  private fun spawnMissingEntities(spawner: Spawner, zone: ZoneServer) {
    if (spawner.spawnedEntities.size >= spawner.maxSpawnCount) {
      return
    }

    val x = randomBetween(spawner.position.x - spawner.range / 2, spawner.position.x + spawner.range / 2)
    val y = randomBetween(spawner.position.y - spawner.range / 2, spawner.position.y + spawner.range / 2)

    // TODO add high check if we get more complex maps
    val spawnedEntityId = bestiaEntityFactory.createMobEntity("blob", Vec3L(x, y, 0L))

    spawner.spawnedEntities.add(spawnedEntityId)
  }

  private fun removeDeadEntities(spawner: Spawner, zone: ZoneServer) {
    spawner.spawnedEntities.removeIf { entityId -> !zone.hasEntity(entityId) }
  }

  fun randomBetween(x: Long, y: Long): Long {
    return Random.nextLong(x, y + 1)
  }
}