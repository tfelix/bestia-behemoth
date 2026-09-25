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
 * Two kinds of rule: the structural item/slot ones, which only [checkEquip] asks, and the ones about the
 * *wearer* - their level against [net.bestia.zone.item.Item.level], and their novicehood against
 * [net.bestia.zone.item.Item.noviceOnly] - which are split into [checkStillWearable] because they are the
 * ones that can stop holding for gear already worn. Callers must handle a [Denial] by re-sending the
 * authoritative [Equipment] component
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
    LEVEL_TOO_LOW,

    /** Novice-only gear on a wearer who has invested outside the Novice tree. */
    NOVICE_ONLY
  }

  /**
   * Returns null when [item] may be worn in [slot], or the reason it may not. [heldUniqueId] is the
   * instance the caller resolved out of [inventory]; 0 means "a plain, not-yet-persisted instance".
   *
   * [wearerLevel] and [wearerIsNovice] describe the wearer and are passed in rather than read here, for the
   * reason this whole service takes plain components: it stays free of the tick thread and directly
   * unit-testable. A caller with no level to offer passes 0 and is refused any item above tier 1, which is
   * the safe direction - an unknown wearer is not a qualified one.
   */
  fun checkEquip(
    equipment: Equipment,
    inventory: Inventory,
    item: Item,
    slot: EquipmentSlot,
    heldUniqueId: Long,
    wearerLevel: Int,
    wearerIsNovice: Boolean
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

    return checkStillWearable(item, wearerLevel, wearerIsNovice)
  }

  /**
   * The rules about the *wearer* alone, which is the subset that can stop holding for something already worn.
   *
   * Split out for [net.bestia.zone.item.equip.EquipmentRevalidationService], which re-asks them over worn gear
   * and cannot call [checkEquip]: that refuses an item it finds already equipped. Two definitions of "may this
   * be worn" would drift, and the one that drifted would be the one nobody tested.
   */
  fun checkStillWearable(item: Item, wearerLevel: Int, wearerIsNovice: Boolean): Denial? {
    // Checked against the template's own level and not against the instance's effective one: an upgrade makes
    // a sword harder to *work on*, not harder to hold, and taking gear away from the player who improved it
    // would punish exactly the thing the upgrade path is for.
    if (wearerLevel < item.level) {
      return Denial.LEVEL_TOO_LOW
    }

    if (item.noviceOnly && !wearerIsNovice) {
      return Denial.NOVICE_ONLY
    }

    return null
  }
}
