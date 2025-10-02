package net.bestia.zone.item

import jakarta.persistence.*
import net.bestia.zone.bestia.Bestia

/**
 * Items which can be dropped by a bestia when it is killed.
 */
@Entity
@Table(name = "loot_item")
class LootItem(

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "bestia_id")
  val bestia: Bestia,

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "item_id")
  val item: Item,

  @Column(nullable = false)
  val dropChance: Int  // It is fixed point: 1_000 means 100%, 100 is 10%, 10 is 1% and 1 0.1%

) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  override fun toString(): String {
    return "LootItem(item=${item.id}, chance=${dropChance / 10.0}%)"
  }
}