package net.bestia.zone.ecs.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.player.Master
import net.bestia.zone.ecs.status.Level
import org.springframework.data.repository.findByIdOrNull
import net.bestia.zone.ecs2.Entity
import net.bestia.zone.ecs2.IteratingSystem
import net.bestia.zone.ecs2.ZoneServer
import org.springframework.stereotype.Component

/**
 * This will identify what type of entity it is and then remove it as some
 * entities like player bestia and  master have different DB tables and are not
 * stored like the regular entities.
 */
@Component
class PersistAndRemoveSystem(
  private val masterRepository: MasterRepository
) : IteratingSystem() {
  override val requiredComponents = setOf(
    PersistAndRemove::class
  )

  override fun update(
    deltaTime: Float,
    entity: Entity,
    zone: ZoneServer
  ) {
    if (entity.has(Master::class)) {
      persistMasterEntity(entity)
    } else {
      LOG.warn { "Found no persistence handler for entity: $entity, it will not be persisted" }
    }
    // Remove the entity from the world
    zone.removeEntity(entity.id)
  }

  private fun persistMasterEntity(entity: Entity) {
    val masterComponent = entity.getOrThrow(Master::class)
    val positionComponent = entity.getOrThrow(Position::class)
    val levelComponent = entity.getOrThrow(Level::class)

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
