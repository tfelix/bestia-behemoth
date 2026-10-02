package net.bestia.zone.item.equip

/**
 * How heavy a piece of armor is. A master wears every type; a bestia only the ones its species lists, because
 * a blob cannot hold plate in place.
 *
 * The **declaration order is a contract** for the same reason as [EquipmentSlot]'s: `Bestia.armorTypeMask` and
 * the client export store [bit]. Append, never reorder.
 */
enum class ArmorType {
  CLOTH,
  LIGHT,
  MEDIUM,
  HEAVY;

  val bit: Int get() = 1 shl ordinal

  companion object {
    val ALL: Int = entries.fold(0) { mask, type -> mask or type.bit }

    fun maskOf(types: Collection<ArmorType>): Int {
      return types.fold(0) { mask, type -> mask or type.bit }
    }
  }
}
