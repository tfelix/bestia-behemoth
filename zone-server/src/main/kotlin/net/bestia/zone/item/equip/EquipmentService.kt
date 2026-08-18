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
 * Two rules today: the structural item/slot one, and the wearer's level against
 * [net.bestia.zone.item.Item.level]. The service exists separately because the rule is still going to grow -
 * a master's gear will also be gated on its learned skills - so a request that is perfectly well-formed has
 * to stay refusable. Callers must handle a [Denial] by re-sending the authoritative [Equipment] component
 * (see [net.bestia.zone.item.equip.EquipItemHandler]) so a client that optimistically moved the item locally
 * snaps back into sync.
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
    NOT_ALLOWED,

    /** The wearer has not reached the item's own level yet - see [net.bestia.zone.item.Item.level]. */
    LEVEL_TOO_LOW
  }

  /**
   * Returns null when [item] may be worn in [slot], or the reason it may not. [heldUniqueId] is the
   * instance the caller resolved out of [inventory]; 0 means "a plain, not-yet-persisted instance".
   *
   * [wearerLevel] is the entity's own level. It is passed in rather than read here for the reason this whole
   * service takes plain components: it stays free of the tick thread and directly unit-testable. A caller with
   * no level to offer passes 0 and is refused any item above tier 1, which is the safe direction - an unknown
   * wearer is not a qualified one.
   */
  fun checkEquip(
    equipment: Equipment,
    inventory: Inventory,
    item: Item,
    slot: EquipmentSlot,
    heldUniqueId: Long,
    wearerLevel: Int
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

    // Checked against the template's own level and not against the instance's effective one: an upgrade makes
    // a sword harder to *work on*, not harder to hold, and taking gear away from the player who improved it
    // would punish exactly the thing the upgrade path is for.
    if (wearerLevel < item.level) {
      return Denial.LEVEL_TOO_LOW
    }

    // TODO Gate on the wearer's learned skills once master gear proficiencies exist; until then a
    //  structurally sound request that clears the level is granted.
    return null
  }
}
