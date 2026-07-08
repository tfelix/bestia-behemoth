package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.SyncContext
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.entity.EntitySMSG


data class ItemVisual(
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

  override fun syncTargets(context: SyncContext, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}
