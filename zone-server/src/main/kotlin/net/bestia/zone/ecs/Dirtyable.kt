package net.bestia.zone.ecs

import net.bestia.zone.message.entity.EntitySMSG

interface Dirtyable {
  fun isDirty(): Boolean
  fun clearDirty()
  fun toEntityMessage(entityId: Long): EntitySMSG
}