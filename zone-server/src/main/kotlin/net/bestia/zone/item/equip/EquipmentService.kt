package net.bestia.zone.item.equip

import net.bestia.zone.ecs.item.Equipment
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.item.Item
import org.springframework.stereotype.Service

/**
 * Decides whether a wearer is allowed to put a given item into a given slot - the single place that
 * answers "may this be equipped", separate from [net.bestia.zone.item.container.ItemContainer],
 * which only knows the structural item/slot rules.
 *
 * Today it accepts everything that is structurally sound. It exists as its own service because the
 * rule is going to grow: a master's gear will be gated on its learned skills, so a request that is
 * perfectly well-formed still has to be refusable. Callers must handle a [Denial] by re-sending the
 * authoritative [Equipment] component (see [net.bestia.zone.item.equip.EquipItemHandler]) so a
 * client that optimistically moved the item locally snaps back into sync.
 *
 * Deliberately takes plain components rather than the ECS world, so it stays free of tick-thread
 * concerns and is directly unit-testable.
 */
@Service
class EquipmentService {

  enum class Denial {
    /** The wearer physically has no such slot (bestia species mask). */
    SLOT_NOT_AVAILABLE,

    /** The item is not held by the wearer at all. */
    ITEM_NOT_FOUND,

    /** Structurally fine, but this wearer may not wear this item. */
    NOT_ALLOWED
  }

  /**
   * Returns null when [item] may be worn in [slot], or the reason it may not. [heldUniqueId] is the
   * instance the caller resolved out of [inventory]; 0 means "a plain, not-yet-persisted instance".
   */
  fun checkEquip(
    equipment: Equipment,
    inventory: Inventory,
    item: Item,
    slot: EquipmentSlot,
    heldUniqueId: Long
  ): Denial? {
    if (item.type != Item.ItemType.EQUIP || item.equipSlot != slot) {
      return Denial.NOT_ALLOWED
    }

    if (!equipment.isSlotAvailable(slot)) {
      return Denial.SLOT_NOT_AVAILABLE
    }

    val isHeld = inventory.getItems().any {
      it.itemId == item.id && (heldUniqueId == 0L || it.uniqueId == heldUniqueId)
    }
    if (!isHeld || equipment.isWorn(heldUniqueId)) {
      return Denial.ITEM_NOT_FOUND
    }

    // TODO Gate on the wearer's learned skills once master gear proficiencies exist; until then a
    //  structurally sound request is always granted.
    return null
  }
}
