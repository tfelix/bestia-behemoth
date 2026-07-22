package net.bestia.zone.item.equip

/**
 * The equipment slots an entity can wear items in - the classic (pre-renewal) Ragnarok Online set.
 *
 * The **declaration order is a contract**: [bit] is derived from the ordinal and that bitmask is
 * what `Bestia.equipSlotMask` stores, what the mob YML importer produces, and what
 * `BestiaDbSyncTask` exports into the Godot client's static bestia DB. Appending new slots (costume
 * or shadow gear) at the end is safe; reordering or removing one silently reinterprets every stored
 * and exported mask.
 */
enum class EquipmentSlot {
  HEAD_UPPER,
  HEAD_MID,
  HEAD_LOWER,
  ARMOR,
  GARMENT,
  FOOTGEAR,
  RIGHT_HAND,
  LEFT_HAND,
  ACCESSORY_1,
  ACCESSORY_2;

  val bit: Int get() = 1 shl ordinal
}

/**
 * Helpers for the `Int` bitmask form of [EquipmentSlot], used wherever a *set* of available slots
 * has to be stored compactly (the `bestia` table, the client `.tres` export) rather than as a
 * relational collection.
 */
object EquipmentSlots {

  /** Every slot - what a master gets, since equip permission is decided by the equip service instead. */
  val ALL: Int = EquipmentSlot.entries.fold(0) { mask, slot -> mask or slot.bit }

  fun maskOf(slots: Collection<EquipmentSlot>): Int = slots.fold(0) { mask, slot -> mask or slot.bit }

  fun maskOf(vararg slots: EquipmentSlot): Int = maskOf(slots.toList())

  fun toSlots(mask: Int): Set<EquipmentSlot> = EquipmentSlot.entries.filter { it.bit and mask != 0 }.toSet()
}

/** True if this slot mask contains [slot]. */
fun Int.hasEquipSlot(slot: EquipmentSlot): Boolean = (this and slot.bit) != 0
