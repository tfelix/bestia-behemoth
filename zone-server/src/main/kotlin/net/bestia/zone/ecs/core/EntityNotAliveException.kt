package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

import net.bestia.zone.BestiaException

/** Thrown when an entity is expected to exist but is not alive. */
class EntityNotAliveException(entityId: EntityId) : BestiaException(
  code = "ENTITY_NOT_ALIVE",
  message = "Entity $entityId is not alive",
)
