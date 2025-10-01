package net.bestia.zone.ecs2

import net.bestia.zone.BestiaException
import net.bestia.zone.util.EntityId

class NoReadLockForEntityException(entityId: EntityId) : BestiaException(
  code = "NO_LOCK_ACQUIRED",
  message = "Could not get a read lock on entity $entityId"
)