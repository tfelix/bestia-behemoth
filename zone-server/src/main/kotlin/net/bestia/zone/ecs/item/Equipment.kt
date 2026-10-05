package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.Dirtyable
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.equip.ArmorType
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.hasEquipSlot
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * The live view of what an entity is wearing, mirroring the [net.bestia.zone.item.equip.EquipmentSlot]
 * markers on its durable [net.bestia.zone.item.container.ItemContainer] slots the same way
 * [Inventory] mirrors the container's stacks.
 *
 * [availableSlotMask] and [wearableArmorTypeMask] are what the entity's *body* allows (everything for a
 * master, the species masks for a bestia). They stay server-side: the client derives the same information
 * from its own static bestia DB, so they are never part of [toEntityMessage].
 */
data class Equipment(
  val availableSlotMask: Int,
  val wearableArmorTypeMask: Int = ArmorType.ALL,
  private val worn: MutableMap<EquipmentSlot, EquippedItem> = mutableMapOf()
) : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  /**
   * One worn item. [uniqueId] is the id of the backing
   * [net.bestia.zone.item.instance.ItemInstance]; equipment is never stackable so it always has
   * one, except in the window between a fresh grant and its async DB write (see [Inventory.Item]).
   */
  data class EquippedItem(
    val itemId: Long,
    val uniqueId: Long,
    /** Mirrors [net.bestia.zone.item.instance.ItemInstance.upgradeLevel] so equip scripts can scale off it. */
    val upgradeLevel: Int = 0,

    /** Wear on the backing instance, both zero for gear that does not wear. */
    val durability: Int = 0,
    val maxDurability: Int = 0,

    /** Rune slots cut into the backing instance. */
    val slots: Int = 0
  )

  fun isSlotAvailable(slot: EquipmentSlot): Boolean = availableSlotMask.hasEquipSlot(slot)

  /** Gear without an armor type is not armor, so every body can wear it. */
  fun canWearArmorType(type: ArmorType?): Boolean {
    return type == null || (wearableArmorTypeMask and type.bit) != 0
  }

  fun get(slot: EquipmentSlot): EquippedItem? = worn[slot]

  fun getWorn(): Map<EquipmentSlot, EquippedItem> = worn.toMap()

  fun isWorn(uniqueId: Long): Boolean = uniqueId != 0L && worn.values.any { it.uniqueId == uniqueId }

  /**
   * Whether a copy of [itemId] - the one named by [uniqueId], or any when that is 0 - can leave [inventory]
   * without taking worn gear along. Equipping writes the database later, so only this component knows a piece
   * was just put on. Worn copies are counted per template, because one worn since its loot still reads id 0.
   */
  fun leavesUnwornCopy(inventory: Inventory, itemId: Long, uniqueId: Long): Boolean {
    val copies = inventory.getItems().filter { it.itemId == itemId }

    if (uniqueId != 0L) {
      return copies.any { it.uniqueId == uniqueId && !it.equipped } && !isWorn(uniqueId)
    }

    return copies.any { it.isStackable } || copies.size > worn.values.count { it.itemId == itemId }
  }

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

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return EquipmentComponentSMSG(
      entityId = entityId,
      items = worn.map { (slot, item) ->
        EquipmentComponentSMSG.EquippedItem(
          slot = slot.ordinal,
          itemId = item.itemId.toInt(),
          uniqueId = item.uniqueId,
          durability = item.durability,
          maxDurability = item.maxDurability,
          slots = item.slots,
          upgradeLevel = item.upgradeLevel
        )
      }
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.OwnerOnly
}
