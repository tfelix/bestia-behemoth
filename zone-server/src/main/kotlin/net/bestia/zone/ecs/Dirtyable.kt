package net.bestia.zone.ecs

import net.bestia.zone.message.entity.EntitySMSG

interface Dirtyable {
  enum class BroadcastType {
    /**
     * Sends a change on this component to anyone in visible range.
     */
    PUBLIC,

    /**
     * Only the entity owner gets an update for this component.
     */
    ONLY_OWNER
  }

  fun isDirty(): Boolean
  fun clearDirty()
  fun toEntityMessage(entityId: Long): EntitySMSG

  fun broadcastType(): BroadcastType
}