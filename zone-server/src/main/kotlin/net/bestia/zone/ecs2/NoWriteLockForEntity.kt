package net.bestia.zone.ecs2

import net.bestia.zone.BestiaException
import net.bestia.zone.util.EntityId

class NoWriteLockForEntity(entityId: EntityId) : BestiaException(
  code = "NO_WRITE_LOCK_ACQUIRED",
  message = "Could not get a write lock on entity $entityId"
)