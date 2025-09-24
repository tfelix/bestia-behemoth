package net.bestia.zone.ecs.persistence

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.World.Companion.inject
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.player.Master
import net.bestia.zone.ecs.status.Level
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional

/**
 * A system which is responsible for all persistable entities but will persist them immediately and
 * then remove them.
 */
class PersistAndRemoveSystem(
  private val masterRepository: MasterRepository = inject()
) : IteratingSystem(
  family { all(Persistent, PersistAndRemove) },
) {
  override fun onTickEntity(entity: Entity) {
    if (entity.has(Master)) {
      persistMasterEntity(entity)
    } else {
      LOG.warn { "Found no persistence handler for entity: $entity, will remove it not" }
    }

    // Remove the entity from the world
    world -= entity
  }

  @Transactional
  private fun persistMasterEntity(entity: Entity) {
    val masterComponent = entity[Master]
    val positionComponent = entity[Position]
    val levelComponent = entity[Level]

    val masterEntity = masterRepository.findByIdOrNull(masterComponent.masterId)

    if (masterEntity == null) {
      LOG.warn { "Master ${masterComponent.masterId} was not found, can not persist it" }
      return
    }

    // Update position
    masterEntity.position = positionComponent.toVec3L()

    // Update level
    masterEntity.level = levelComponent.level

    // Save the updated master entity
    masterRepository.save(masterEntity)

    LOG.info { "Successfully persisted master ${masterEntity.id} at position ${masterEntity.position} with level ${masterEntity.level}" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
