package net.bestia.zone.ecs2

import net.bestia.zone.util.EntityId

interface ZoneOperations {
  fun addEntity(): EntityId
  fun removeEntity(entityId: EntityId)
  fun hasEntity(entityId: EntityId): Boolean
  fun <T> addEntityWithWriteLock(action: (Entity) -> T): EntityId

  fun <T> withEntityReadLock(entityId: EntityId, action: (Entity) -> T): T?
  fun <T> withEntityWriteLock(entityId: EntityId, action: (Entity) -> T): T?
  fun <T> withEntityReadLockOrThrow(entityId: EntityId, action: (Entity) -> T): T

  // fun <T> withEntityWriteLockOrThrow(entityId: EntityId, action: (Entity) -> T): T
  // fun scheduleJob(delaySeconds: Long, action: (ZoneOperations) -> Unit)
  fun queueExternalJob(action: () -> Unit)
}

