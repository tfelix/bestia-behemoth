package net.bestia.zone.ecs

import net.bestia.zone.message.entity.EntitySMSG

/**
 * Signals if a component is dirty and needs to be send over the network.
 */
interface Dirtyable {
  fun isDirty(): Boolean
  fun clearDirty()
  fun toEntityMessage(entityId: Long): EntitySMSG
}