package net.bestia.zone.account.master

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.persistence.ComponentEntityWriter
import net.bestia.zone.util.EntityId
import org.springframework.transaction.annotation.Transactional

abstract class EntityPersistenceService<T>(
  private val worldView: WorldView,
  private val writers: List<ComponentEntityWriter<*, T>>,
) {

  /**
   * Must be open so springs proxy can override it in the child classes and properly open
   * a transaction.
   */
  @Transactional
  open fun persistEntity(entityId: EntityId, dbEntityId: Long) {
    val dbEntity = loadEntity(dbEntityId)
    worldView.read {
      writers.forEach { writer ->
        writer.persistChanges(this, entityId, dbEntity)
      }
    }
    saveEntity(dbEntity)
  }

  abstract fun loadEntity(entityId: Long): T
  abstract fun saveEntity(entity: T)
}