package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * The public face of [Equipment]: which item sits in which slot, so every client in range can draw it.
 * [WornGearSystem] keeps it in step with [Equipment], which stays owner-only.
 */
class WornGear : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  var itemIds: Map<EquipmentSlot, Long> = emptyMap()
    private set

  /** Marks the component dirty only when the worn items really changed. */
  fun show(worn: Map<EquipmentSlot, Long>) {
    if (worn == itemIds) {
      return
    }
    itemIds = worn
    markDirty()
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return WornGearComponentSMSG(entityId, itemIds.map { (slot, itemId) -> slot.ordinal to itemId.toInt() })
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}
