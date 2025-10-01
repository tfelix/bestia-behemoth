package net.bestia.zone.ecs2

import net.bestia.zone.message.entity.EntitySMSG

interface Component {

}

interface Dirtyable {
  fun isDirty(): Boolean
  fun clearDirty()
  fun toEntityMessage(entityId: Long): EntitySMSG
}