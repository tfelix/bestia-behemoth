package net.bestia.memoryserver.persistance

import net.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.TagComponent
import bestia.model.dao.EntityDataDAO
import bestia.model.domain.EntityData
import bestia.util.ObjectSerializer
import org.slf4j.LoggerFactory
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.stereotype.Service
import java.util.*

@Service
class EntityPersistService(
        private val entityDao: EntityDataDAO,
        private val entityService: EntityService
) {

  companion object {
    private val LOG = LoggerFactory.getLogger(EntityPersistService::class.java)
  }

  private val serializer = ObjectSerializer<Entity>()

  /**
   * Deletes the entity and all attached components from the permanent
   * storage.
   *
   * @param id
   * Deletes the entity and all attached component from the
   * storage.
   */
  fun delete(id: Long?) {
    try {
      entityDao.delete(id)
    } catch (e: EmptyResultDataAccessException) {
      // Entity did not exist. Not important. Ignore.
    }

  }

  /**
   * Loads the entity with the given id from the permanent storage.
   */
  fun load(id: Long?): Entity? {

    val data = entityDao.findOne(id)

    if (data == null) {
      LOG.debug("Did not find entity {} inside database. Returning null.", id)
      return null
    }

    val entity = serializer.deserialize(data.data)

    LOG.debug("Loaded entity: {} for id: {}.", entity, id)

    return entity
  }

  /**
   * Stores the entity into the permanent storage.
   *
   * @param entity
   * The entity to be permanently stored.
   */
  fun store(entity: Entity) {
    Objects.requireNonNull(entity)
    LOG.trace("store(): {}", entity)

    // Only store if it was flagged with a tag.
    val tagComp = entityService.getComponent(entity, TagComponent::class.java)

    if (!tagComp.isPresent || !tagComp.get().has(TagComponent.Tag.PERSIST)) {
      LOG.trace("Entity {} not tagged with PERSIST. Dont store.", entity)
      return
    }

    LOG.debug("Entity {} is tagged with PERSIST. Storing it.", entity)

    // Find all the components of the entity.
    val data = serializer.serialize(entity)
    val entityData = EntityData()
    entityData.id = entity.id
    entityData.data = data
    entityDao.save(entityData)
  }
}