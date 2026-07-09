package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

import net.bestia.zone.BestiaException

/**
 * Thrown by [World.modifyOrThrow]/[World.getOrThrow] paths when an entity is expected to exist but
 * is not alive. Replaces the previous `net.bestia.zone.ecs.NoReadLockForEntityException`.
 */
class EntityNotAliveException(entityId: EntityId) : BestiaException(
  code = "ENTITY_NOT_ALIVE",
  message = "Entity $entityId is not alive",
)
