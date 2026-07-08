package net.bestia.zone.ecs

import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.message.entity.EntitySMSG

interface Dirtyable {
  fun isDirty(): Boolean
  fun clearDirty()
  fun toEntityMessage(entityId: Long): EntitySMSG

  /**
   * Who should receive this change this tick, resolved fresh every flush so it can depend on
   * live state (party membership, ...) rather than a fixed per-type rule.
   */
  fun syncTargets(context: SyncContext, entityId: EntityId): SyncTargets
}
