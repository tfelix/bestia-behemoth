package net.bestia.zone.item.ecs

import net.bestia.zone.item.PlayerItemIds
import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.stereotype.Component

/**
 * Puts gear on and takes it off in both live views, [Equipment] and [Inventory], at once.
 *
 * Every path that changes a slot goes through here, so the bag's equipped marker can never drift from what
 * the slot holds. The database side has its own single path, `InventoryService.equip/unequip`.
 */
@Component
class EquipmentChanges(
  private val templates: ItemTemplateRegistry
) {

  /**
   * Wears the [held] bag item in [slot]. Answers what is worn now, or null without changing anything when the
   * slot is missing or taken - the caller asked [net.bestia.zone.item.equip.EquipmentService] first.
   *
   * A pile is not worn whole: one copy is split off as the instance [newUniqueId], so it can burn down on its
   * own. `InventoryService.equipFromPile` is the database half.
   */
  fun putOn(
    equipment: Equipment,
    inventory: Inventory,
    slot: EquipmentSlot,
    held: Inventory.Item,
    itemLevel: Int,
    newUniqueId: () -> Long = PlayerItemIds::next
  ): Equipment.EquippedItem? {
    if (!equipment.isSlotAvailable(slot) || equipment.get(slot) != null) {
      return null
    }
    val copy = if (held.isStackable) {
      val maxDurability = templates.templateOf(held.itemId)?.maxDurability ?: 0
      inventory.splitOff(held, newUniqueId(), maxDurability) ?: return null
    } else {
      held
    }

    val worn = Equipment.EquippedItem(
      itemId = copy.itemId,
      uniqueId = copy.uniqueId,
      // Carried over from the inventory view rather than re-read from the DB: the wear a client
      // already sees on the item in the bag must be the wear it sees on the item in the slot.
      upgradeLevel = copy.upgradeLevel,
      durability = copy.durability,
      maxDurability = copy.maxDurability,
      slots = copy.slots,
      itemLevel = itemLevel,
      rolledStats = copy.rolledStats,
      provenance = copy.provenance
    )
    equipment.equip(slot, worn)
    inventory.setEquipped(worn.uniqueId, true)

    return worn
  }

  /**
   * Takes off whatever is worn in [slot] and answers it, or null when the slot was empty. An unused copy of
   * stackable gear goes back into its pile, as `InventoryService.unequip` does in the database.
   */
  fun takeOff(equipment: Equipment, inventory: Inventory?, slot: EquipmentSlot): Equipment.EquippedItem? {
    val removed = equipment.unequip(slot) ?: return null
    if (foldsBack(removed)) {
      inventory?.foldBack(removed.uniqueId)
    } else {
      inventory?.setEquipped(removed.uniqueId, false)
    }

    return removed
  }

  private fun foldsBack(worn: Equipment.EquippedItem): Boolean {
    return templates.templateOf(worn.itemId)?.stackable == true && worn.durability == worn.maxDurability
  }

  /** Puts [item] back into [slot] as it was, for a swap that has to be undone. */
  fun restore(equipment: Equipment, inventory: Inventory, slot: EquipmentSlot, item: Equipment.EquippedItem) {
    if (equipment.equip(slot, item)) {
      inventory.setEquipped(item.uniqueId, true)
    }
  }
}
