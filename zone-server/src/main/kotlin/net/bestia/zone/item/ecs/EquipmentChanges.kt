package net.bestia.zone.item.ecs

import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.stereotype.Component

/**
 * Puts gear on and takes it off in both live views, [Equipment] and [Inventory], at once.
 *
 * Every path that changes a slot goes through here, so the bag's equipped marker can never drift from what
 * the slot holds. The database side has its own single path, `InventoryService.equip/unequip`.
 */
@Component
class EquipmentChanges {

  /**
   * Wears the [held] bag item in [slot]. Answers what is worn now, or null without changing anything when the
   * slot is missing or taken - the caller asked [net.bestia.zone.item.equip.EquipmentService] first.
   */
  fun putOn(
    equipment: Equipment,
    inventory: Inventory,
    slot: EquipmentSlot,
    held: Inventory.Item,
    itemLevel: Int
  ): Equipment.EquippedItem? {
    val worn = Equipment.EquippedItem(
      itemId = held.itemId,
      uniqueId = held.uniqueId,
      // Carried over from the inventory view rather than re-read from the DB: the wear a client
      // already sees on the item in the bag must be the wear it sees on the item in the slot.
      upgradeLevel = held.upgradeLevel,
      durability = held.durability,
      maxDurability = held.maxDurability,
      slots = held.slots,
      itemLevel = itemLevel,
      rolledStats = held.rolledStats,
      provenance = held.provenance
    )
    if (!equipment.equip(slot, worn)) {
      return null
    }
    inventory.setEquipped(worn.uniqueId, true)

    return worn
  }

  /** Takes off whatever is worn in [slot] and answers it, or null when the slot was empty. */
  fun takeOff(equipment: Equipment, inventory: Inventory?, slot: EquipmentSlot): Equipment.EquippedItem? {
    val removed = equipment.unequip(slot) ?: return null
    inventory?.setEquipped(removed.uniqueId, false)

    return removed
  }

  /** Puts [item] back into [slot] as it was, for a swap that has to be undone. */
  fun restore(equipment: Equipment, inventory: Inventory, slot: EquipmentSlot, item: Equipment.EquippedItem) {
    if (equipment.equip(slot, item)) {
      inventory.setEquipped(item.uniqueId, true)
    }
  }
}
