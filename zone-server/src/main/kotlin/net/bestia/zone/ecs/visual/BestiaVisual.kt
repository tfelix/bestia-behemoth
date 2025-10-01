package net.bestia.zone.ecs.visual

import net.bestia.zone.ecs2.Component
import net.bestia.zone.ecs2.Dirtyable
import net.bestia.zone.message.entity.EntitySMSG

data class BestiaVisual(
  val id: Int
) : Component, Dirtyable {

  private var dirty = true

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return BestiaVisualComponentSMSG(entityId, id)
  }
}