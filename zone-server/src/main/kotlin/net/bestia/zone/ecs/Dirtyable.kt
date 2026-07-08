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

sealed interface SyncTargets {
  /** Broadcast to every player currently in AOI range of the entity's position. */
  data object PublicInRange : SyncTargets

  /** Send only to these specific accounts (e.g. the owner, plus party members). */
  data class Accounts(val accountIds: Set<Long>) : SyncTargets
}