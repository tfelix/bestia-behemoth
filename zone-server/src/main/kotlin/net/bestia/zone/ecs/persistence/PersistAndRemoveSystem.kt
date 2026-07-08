package net.bestia.zone.ecs.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.player.Master
import net.bestia.zone.ecs.status.Level
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.Ecs2System
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.data.repository.findByIdOrNull
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

/**
 * This will identify what type of entity it is and then remove it as some
 * entities like player bestia and  master have different DB tables and are not
 * stored like the regular entities.
 */
@SpringComponent
@Order(90)
class PersistAndRemoveSystem(
  private val masterRepository: MasterRepository
) : Ecs2System {

  override val reads: Set<KClass<out Component>> =
    setOf(PersistAndRemove::class, Master::class, Position::class, Level::class)

  override fun update(world: World, deltaTime: Float) {
    val toRemove = mutableListOf<EntityId>()
    world.query(PersistAndRemove::class).each { id -> toRemove.add(id) }

    for (id in toRemove) {
      if (world.has(id, Master::class)) {
        persistMasterEntity(world, id)
      } else {
        LOG.warn { "Found no persistence handler for entity: $id, it will not be persisted" }
      }
      world.destroy(id)
    }
  }

  private fun persistMasterEntity(world: World, id: EntityId) {
    val masterComponent = world.getOrThrow(id, Master::class)
    val positionComponent = world.getOrThrow(id, Position::class)
    val levelComponent = world.getOrThrow(id, Level::class)

    val masterEntity = masterRepository.findByIdOrNull(masterComponent.masterId)

    if (masterEntity == null) {
      LOG.warn { "Master ${masterComponent.masterId} was not found, can not persist it" }
      return
    }

    masterEntity.position = positionComponent.toVec3L()
    masterEntity.level = levelComponent.level
    masterRepository.save(masterEntity)
    LOG.info { "Successfully persisted master ${masterEntity.id} at position ${masterEntity.position} with level ${masterEntity.level}" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
