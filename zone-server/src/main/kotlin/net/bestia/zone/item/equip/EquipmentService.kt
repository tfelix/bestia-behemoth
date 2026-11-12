package net.bestia.zone.item.equip

import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.item.profession.GearAllowance
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.persistence.Item
import org.springframework.stereotype.Service

/**
 * Decides whether a wearer is allowed to put a given item into a given slot - the single place that
 * answers "may this be equipped", separate from [net.bestia.zone.item.persistence.ItemContainer],
 * which only knows the structural item/slot rules.
 *
 * Two kinds of rule: the structural item/slot ones, which only [checkEquip] asks, and the ones about the
 * *wearer* - their level against the copy's item level, and their novicehood against
 * [net.bestia.zone.item.persistence.Item.noviceOnly] - which are split into [checkStillWearable] because they are the
 * ones that can stop holding for gear already worn. Callers must handle a [Denial] by re-sending the
 * authoritative [Equipment] component
 * (see [net.bestia.zone.item.net.EquipItemHandler]) so a client that optimistically moved the item locally
 * snaps back into sync.
 *
 * Deliberately takes plain components rather than the ECS world, so it stays free of tick-thread
 * concerns and is directly unit-testable.
 */
@Service
class EquipmentService(
  private val templates: ItemTemplateRegistry
) {

  enum class Denial {
    /** The wearer physically has no such slot (bestia species mask). */
    SLOT_NOT_AVAILABLE,

    /** The wearer's body cannot carry armor of this weight (bestia species mask). */
    ARMOR_TYPE_NOT_WEARABLE,

    /** The item is not held by the wearer at all. */
    ITEM_NOT_FOUND,

    /** Structurally fine, but this wearer may not wear this item. */
    NOT_ALLOWED,

    /** The wearer has not reached the item level of this copy yet. */
    LEVEL_TOO_LOW,

    /** Novice-only gear on a wearer who has invested outside the Novice tree. */
    NOVICE_ONLY,

    /** A two-handed weapon and something in the left hand at once. */
    HANDS_FULL,

    /** Worn down to nothing; it has to be repaired first. */
    BROKEN,

    /** A strip skill emptied the slot a moment ago, see [SlotLock]. */
    SLOT_STRIPPED,

    /** Neither of the master's professions allows this weapon type or armor weight. */
    NOT_YOUR_PROFESSION,

    /** A weapon in the left hand without the Dual Wield skill. */
    NEEDS_DUAL_WIELD,
  }

  /**
   * Returns null when [item] may be worn in [slot], or the reason it may not. [heldUniqueId] is the
   * instance the caller resolved out of [inventory]; 0 means "a plain, not-yet-persisted instance".
   *
   * [wearerLevel] and [wearerIsNovice] describe the wearer and are passed in rather than read here, for the
   * reason this whole service takes plain components: it stays free of the tick thread and directly
   * unit-testable. A caller with no level to offer passes 0 and is refused any item above tier 1, which is
   * the safe direction - an unknown wearer is not a qualified one. [canDualWield] is whether the wearer has learned
   * Dual Wield, which a second weapon in the left hand needs.
   */
  fun checkEquip(
    equipment: Equipment,
    inventory: Inventory,
    item: Item,
    itemLevel: Int,
    slot: EquipmentSlot,
    heldUniqueId: Long,
    wearerLevel: Int,
    wearerIsNovice: Boolean,
    lockedSlots: Set<EquipmentSlot> = emptySet(),
    allowance: GearAllowance? = null,
    canDualWield: Boolean = false,
  ): Denial? {
    if (item.type != Item.ItemType.EQUIP || !item.fitsSlot(slot)) {
      return Denial.NOT_ALLOWED
    }

    if (!equipment.isSlotAvailable(slot)) {
      return Denial.SLOT_NOT_AVAILABLE
    }

    if (slot in lockedSlots) {
      return Denial.SLOT_STRIPPED
    }

    if (!equipment.canWearArmorType(item.armorType)) {
      return Denial.ARMOR_TYPE_NOT_WEARABLE
    }

    val held = inventory.getItems().firstOrNull {
      it.itemId == item.id && (heldUniqueId == 0L || it.uniqueId == heldUniqueId)
    }
    if (held == null || equipment.isWorn(heldUniqueId)) {
      return Denial.ITEM_NOT_FOUND
    }

    if (held.isBroken) {
      return Denial.BROKEN
    }

    if (isSecondWeapon(item, slot) && !canDualWield) {
      return Denial.NEEDS_DUAL_WIELD
    }

    if (wouldOverfillHands(equipment, item, slot)) {
      return Denial.HANDS_FULL
    }

    return checkStillWearable(item, itemLevel, wearerLevel, wearerIsNovice, allowance)
  }

  private fun isSecondWeapon(item: Item, slot: EquipmentSlot): Boolean {
    return slot == EquipmentSlot.LEFT_HAND && item.equipSlot != EquipmentSlot.LEFT_HAND
  }

  /** Refused rather than taking the other hand off, so nothing leaves a slot the player did not touch. */
  private fun wouldOverfillHands(equipment: Equipment, item: Item, slot: EquipmentSlot): Boolean {
    if (item.twoHanded) {
      return equipment.get(EquipmentSlot.LEFT_HAND) != null
    }

    val rightHand = equipment.get(EquipmentSlot.RIGHT_HAND) ?: return false

    return slot == EquipmentSlot.LEFT_HAND && templates.templateOf(rightHand.itemId)?.twoHanded == true
  }

  /**
   * The rules about the *wearer* alone, which is the subset that can stop holding for something already worn.
   *
   * Split out for [net.bestia.zone.item.equip.EquipmentRevalidationService], which re-asks them over worn gear
   * and cannot call [checkEquip]: that refuses an item it finds already equipped. Two definitions of "may this
   * be worn" would drift, and the one that drifted would be the one nobody tested.
   */
  fun checkStillWearable(
    item: Item,
    itemLevel: Int,
    wearerLevel: Int,
    wearerIsNovice: Boolean,
    allowance: GearAllowance? = null,
  ): Denial? {
    // The copy's own level, stats included, but without its upgrade level: an upgrade makes a sword harder to
    // *work on*, not harder to hold, and taking gear away from the player who improved it would punish exactly
    // the thing the upgrade path is for.
    if (wearerLevel < itemLevel) {
      return Denial.LEVEL_TOO_LOW
    }

    if (item.noviceOnly && !wearerIsNovice) {
      return Denial.NOVICE_ONLY
    }

    // Null for a bestia, which only its species limits.
    if (allowance != null && !allowance.allows(item)) {
      return Denial.NOT_YOUR_PROFESSION
    }

    return null
  }
}
