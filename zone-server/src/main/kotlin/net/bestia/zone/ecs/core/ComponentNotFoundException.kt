package net.bestia.zone.ecs.core

import net.bestia.zone.BestiaException
import kotlin.reflect.KClass

/**
 * Thrown by [World.getOrThrow] when a required component is missing on an entity. Mirrors the
 * previous `net.bestia.zone.ecs.ComponentNotFoundException` so callers that relied on catching it
 * (e.g. building entity update messages) keep working.
 */
class ComponentNotFoundException(
  entityId: EntityId,
  type: KClass<out Component>,
) : BestiaException(
  code = "COMP_NOT_FOUND",
  message = "Component ${type.simpleName} was not found on entity $entityId",
)
