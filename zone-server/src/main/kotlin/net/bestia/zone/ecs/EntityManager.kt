package net.bestia.zone.ecs

import net.bestia.zone.util.EntityId
import java.util.concurrent.ConcurrentHashMap

class EntityManager {
  private val entities = ConcurrentHashMap<EntityId, Entity>()
  private val entityLocks = ConcurrentHashMap<EntityId, EntityLock>()
  private val entityIdGenerator = EntityIdGenerator(1)

  fun createEntity(): Entity {
    val id = entityIdGenerator.nextId()
    val entity = Entity(id)
    entities[id] = entity
    entityLocks[id] = EntityLock(entity)

    return entity
  }

  fun getEntity(id: EntityId): Entity? {
    return entities[id]
  }

  fun removeEntity(id: EntityId): Entity? {
    entityLocks.remove(id)?.close()

    return entities.remove(id)
  }

  fun getAllEntities(): Collection<Entity> {
    return entities.values
  }

  // Thread-safe entity access
  fun <T> withEntityReadLock(entityId: EntityId, action: (Entity) -> T): T? {
    val entity = entities[entityId] ?: return null
    val entityLock = entityLocks[entityId] ?: return null

    entityLock.acquireReadLock()
    try {
      return action(entity)
    } finally {
      entityLock.close()
    }
  }

  fun <T> withEntityWriteLock(entityId: EntityId, action: (Entity) -> T): T? {
    val entity = entities[entityId] ?: return null
    val entityLock = entityLocks[entityId] ?: return null

    entityLock.acquireWriteLock()
    try {
      return action(entity)
    } finally {
      entityLock.close()
    }
  }

  fun hasEntity(entityId: EntityId): Boolean {
    return entities.containsKey(entityId)
  }
}