package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.hasEquipSlot
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * The live view of what an entity is wearing, mirroring the [net.bestia.zone.item.equip.EquipmentSlot]
 * markers on its durable [net.bestia.zone.item.container.ItemContainer] slots the same way
 * [Inventory] mirrors the container's stacks.
 *
 * [availableSlotMask] is the entity's *physical* slot set (all slots for a master, the species mask
 * for a bestia). It stays server-side: the client derives the same information from its own static
 * bestia DB, so it is never part of [toEntityMessage].
 */
data class Equipment(
  val availableSlotMask: Int,
  private val worn: MutableMap<EquipmentSlot, EquippedItem> = mutableMapOf()
) : Component, Dirtyable {

  private var dirty = true

  /**
   * One worn item. [uniqueId] is the id of the backing
   * [net.bestia.zone.item.instance.ItemInstance]; equipment is never stackable so it always has
   * one, except in the window between a fresh grant and its async DB write (see [Inventory.Item]).
   */
  data class EquippedItem(
    val itemId: Long,
    val uniqueId: Long,
    /** Mirrors [net.bestia.zone.item.instance.ItemInstance.upgradeLevel] so equip scripts can scale off it. */
    val upgradeLevel: Int = 0
  )

  fun isSlotAvailable(slot: EquipmentSlot): Boolean = availableSlotMask.hasEquipSlot(slot)

  fun get(slot: EquipmentSlot): EquippedItem? = worn[slot]

  fun getWorn(): Map<EquipmentSlot, EquippedItem> = worn.toMap()

  fun isWorn(uniqueId: Long): Boolean = uniqueId != 0L && worn.values.any { it.uniqueId == uniqueId }

  /**
   * Puts [item] into [slot]. Returns false without changing anything if the entity has no such slot
   * or it is already occupied - the caller is expected to have asked
   * [net.bestia.zone.item.equip.EquipmentService] first, this is only the last structural guard.
   */
  fun equip(slot: EquipmentSlot, item: EquippedItem): Boolean {
    if (!isSlotAvailable(slot) || worn.containsKey(slot)) {
      return false
    }

    worn[slot] = item
    markDirty()

    return true
  }

  fun unequip(slot: EquipmentSlot): EquippedItem? {
    val removed = worn.remove(slot) ?: return null
    markDirty()

    return removed
  }

  override fun isDirty(): Boolean = dirty

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return EquipmentComponentSMSG(
      entityId = entityId,
      items = worn.map { (slot, item) ->
        EquipmentComponentSMSG.EquippedItem(
          slot = slot.ordinal,
          itemId = item.itemId.toInt(),
          uniqueId = item.uniqueId
        )
      }
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.OwnerOnly
}
