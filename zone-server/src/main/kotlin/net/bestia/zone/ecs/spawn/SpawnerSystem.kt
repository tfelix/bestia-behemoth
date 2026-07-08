package net.bestia.zone.ecs.spawn

import net.bestia.zone.bestia.BestiaEntityFactory
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.Ecs2System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import org.springframework.core.annotation.Order
import kotlin.random.Random
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(80)
class SpawnerSystem(
  private val bestiaEntityFactory: BestiaEntityFactory,
) : Ecs2System {

  override val writes: Set<KClass<out Component>> = setOf(Spawner::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Spawner::class).each {
      val spawner = get<Spawner>()
      removeDeadEntities(spawner, world)
      spawnMissingEntities(spawner)
    }
  }

  private fun spawnMissingEntities(spawner: Spawner) {
    if (spawner.spawnedEntities.size >= spawner.maxSpawnCount) {
      return
    }

    val x = randomBetween(spawner.position.x - spawner.range / 2, spawner.position.x + spawner.range / 2)
    val y = randomBetween(spawner.position.y - spawner.range / 2, spawner.position.y + spawner.range / 2)

    // TODO add high check if we get more complex maps
    val spawnedEntityId = bestiaEntityFactory.createMobEntity("blob", Vec3L(x, y, 0L))

    spawner.spawnedEntities.add(spawnedEntityId)
  }

  private fun removeDeadEntities(spawner: Spawner, world: World) {
    spawner.spawnedEntities.removeIf { entityId -> !world.hasEntity(entityId) }
  }

  fun randomBetween(x: Long, y: Long): Long {
    return Random.nextLong(x, y + 1)
  }
}
