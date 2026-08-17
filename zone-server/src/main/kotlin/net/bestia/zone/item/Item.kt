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
  var weight: Int,
  var type: ItemType,

  /**
   * Whether fresh grants of this item merge into a single stack. Items that carry per-instance
   * state ([net.bestia.zone.item.instance.ItemInstance] - upgrade level, forged-by-master, ...)
   * are never stacked regardless of this flag; this only decides how a plain, freshly obtained
   * item is stored. Equipment defaults to non-stackable.
   */
  var stackable: Boolean = (type != ItemType.EQUIP),

  /**
   * Name of the [net.bestia.zone.item.script.ItemScript] implementation used to execute this
   * item's usage effect. Required for [ItemType.USABLE] items.
   */
  @Column(nullable = true)
  var script: String? = null,

  /**
   * The single slot this item is worn in. Required for [ItemType.EQUIP] and always null otherwise.
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = true)
  var equipSlot: EquipmentSlot? = null,

  /**
   * Long-form flavor text, English only.
   */
  @Column(columnDefinition = "TEXT", nullable = true)
  var description: String? = null
) {

  init {
    requireValidIdentifier(identifier)
    validate()
  }

  /**
   * The invariants tying [type] to [script] and [equipSlot].
   *
   * Extracted from `init` rather than left inline because the importer now *updates* an existing row in
   * place, and an `init` block only ever guards the construction path - an item edited from USABLE to ETC
   * without dropping its script would sail straight past it.
   */
  fun validate() {
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
