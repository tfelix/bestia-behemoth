package net.bestia.zone.ecs.spawn

import net.bestia.zone.bestia.BestiaEntityFactory
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import org.springframework.core.annotation.Order
import kotlin.random.Random
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(80)
class SpawnerSystem(
  private val bestiaEntityFactory: BestiaEntityFactory,
) : System {

  override val writes: ComponentClassSet = setOf(Spawner::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Spawner::class).each {
      val spawner = get<Spawner>()
      removeDeadEntities(spawner, world)
      spawnMissingEntities(spawner, world)
    }
  }

  private fun spawnMissingEntities(spawner: Spawner, world: World) {
    if (spawner.spawnedEntities.size >= spawner.maxSpawnCount) {
      return
    }

    val x = randomBetween(spawner.position.x - spawner.range / 2, spawner.position.x + spawner.range / 2)
    val y = randomBetween(spawner.position.y - spawner.range / 2, spawner.position.y + spawner.range / 2)

    // TODO add high check if we get more complex maps
    val spawnedEntityId = bestiaEntityFactory.createMobEntity(world, "blob", Vec3L(x, y, 0L))

    spawner.spawnedEntities.add(spawnedEntityId)
  }

  private fun removeDeadEntities(spawner: Spawner, world: World) {
    spawner.spawnedEntities.removeIf { entityId -> !world.hasEntity(entityId) }
  }

  fun randomBetween(x: Long, y: Long): Long {
    return Random.nextLong(x, y + 1)
  }
}
