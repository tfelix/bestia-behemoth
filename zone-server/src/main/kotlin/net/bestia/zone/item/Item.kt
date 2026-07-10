package net.bestia.zone.item

import jakarta.persistence.*
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
   * Name of the [net.bestia.zone.item.script.ItemScript] implementation used to execute this
   * item's usage effect. Required for [ItemType.USABLE] items.
   */
  @Column(nullable = true)
  val script: String? = null,

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
  }

  enum class ItemType {
    USABLE,
    EQUIP,
    ETC
  }
}
