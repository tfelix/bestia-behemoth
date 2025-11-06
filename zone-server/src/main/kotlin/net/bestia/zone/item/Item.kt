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
  val type: ItemType
) {

  init {
    requireValidIdentifier(identifier)
  }

  enum class ItemType {
    USABLE,
    EQUIP,
    ETC
  }
}
