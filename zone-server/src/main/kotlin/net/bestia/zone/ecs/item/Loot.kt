package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.Component
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.visual.ItemVisualComponentSMSG
import net.bestia.zone.message.entity.EntitySMSG


data class Loot(
  val itemId: Long,
  val amount: Int,
  val uniqueId: Long = 0 // 0 means nothing special.
) : Component, Dirtyable {

  private var dirty = true

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return ItemVisualComponentSMSG(entityId, itemId.toInt(), amount, uniqueId)
  }

  override fun broadcastType(): Dirtyable.BroadcastType {
    return Dirtyable.BroadcastType.PUBLIC
  }
}
