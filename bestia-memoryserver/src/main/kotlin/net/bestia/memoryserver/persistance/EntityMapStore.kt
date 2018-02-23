package net.bestia.memoryserver.persistance

import net.entity.Entity
import com.hazelcast.core.MapStore
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Component
import java.util.*

/**
 * Specialized class which enables the entities to be persistedly stored into a
 * database. This is needed if the bestia server goes down or is shut down so
 * the world keeps persisted and can be reloaded. This store classes synchronize
 * in memory objects with persistent databases.
 *
 * @author Thomas Felix
 */

/**
 * Ctor. EntityPersistService must be marked with Lazy since it has a
 * indirect dependency to a [CrudRepository] which in turn uses a
 * hazelcast instance because of caching. This forms a circular dependency.
 *
 * @param entityPersistService
 */
@Component
class EntityMapStore(
        @Lazy private var entityPersistService: EntityPersistService
) : MapStore<Long, Entity> {

  @Synchronized
  override fun load(id: Long?): Entity? {

    LOG.trace("Loading entity: {}.", id)

    return entityPersistService.load(id)
  }

  @Synchronized
  override fun loadAll(ids: Collection<Long>): Map<Long, Entity> {

    val entities = HashMap<Long, Entity>()
    ids.forEach { id -> entityPersistService.load(id)?.let { entities[id] = it } }
    return entities
  }

  @Synchronized
  override fun loadAllKeys(): Iterable<Long>? {
    // All loading not supported at the moment since we can not easily
    // iterate over the SQL keys. Maybe implement later when there is more
    // time.
    return null
  }

  @Synchronized
  override fun delete(id: Long?) {

    LOG.trace("Deleting entity: {}.", id)

    entityPersistService.delete(id)
  }

  @Synchronized
  override fun deleteAll(ids: Collection<Long>) {

    LOG.trace("Deleting all {} entities.", ids.size)

    for (id in ids) {
      entityPersistService.delete(id)
    }

  }

  @Synchronized
  override fun store(id: Long?, entity: Entity) {

    LOG.trace("Store entity: {}", entity)

    entityPersistService.store(entity)

  }

  @Synchronized
  override fun storeAll(entities: Map<Long, Entity>) {

    LOG.trace("Store all {} entities.", entities)

    for (entity in entities.values) {
      entityPersistService.store(entity)
    }
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(EntityMapStore::class.java)
  }
}
