package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG


data class ItemVisual(
  val itemId: Long,
  val amount: Int,
  val uniqueId: Long = 0 // id of the backing ItemInstance; 0 means a plain item with no instance.
) : Component, Dirtyable {

  private var dirty = true

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return ItemVisualComponentSMSG(entityId, itemId.toInt(), amount, uniqueId)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}
