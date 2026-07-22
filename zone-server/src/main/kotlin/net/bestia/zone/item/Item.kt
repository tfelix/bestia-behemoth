package net.bestia.zone.item

import jakarta.persistence.*
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.util.requireValidIdentifier

@Entity
@Table(
  name = "item",
  indexes = [
    Index(columnList = "identifier", unique = true)
  ]
)
class Item(
  @Id
  var id: Long = 0,

  val identifier: String,
  /**
   * 10 weight roughly equals 1kg.
   */
  val weight: Int,
  val type: ItemType,

  /**
   * Whether fresh grants of this item merge into a single stack. Items that carry per-instance
   * state ([net.bestia.zone.item.instance.ItemInstance] - upgrade level, forged-by-master, ...)
   * are never stacked regardless of this flag; this only decides how a plain, freshly obtained
   * item is stored. Equipment defaults to non-stackable.
   */
  val stackable: Boolean = (type != ItemType.EQUIP),

  /**
   * Name of the [net.bestia.zone.item.script.ItemScript] implementation used to execute this
   * item's usage effect. Required for [ItemType.USABLE] items.
   */
  @Column(nullable = true)
  val script: String? = null,

  /**
   * The single slot this item is worn in. Required for [ItemType.EQUIP] and always null otherwise.
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = true)
  val equipSlot: EquipmentSlot? = null,

  /**
   * Long-form flavor text, English only.
   */
  @Column(columnDefinition = "TEXT", nullable = true)
  val description: String? = null
) {

  init {
    requireValidIdentifier(identifier)

    if (type == ItemType.USABLE) {
      requireNotNull(script) {
        "Item $identifier is USABLE and must have a script attached"
      }
    }

    if (type == ItemType.EQUIP) {
      requireNotNull(equipSlot) {
        "Item $identifier is EQUIP and must declare which equipSlot it is worn in"
      }
    } else {
      require(equipSlot == null) {
        "Item $identifier is $type and must not declare an equipSlot"
      }
    }
  }

  enum class ItemType {
    USABLE,
    EQUIP,
    ETC
  }
}
