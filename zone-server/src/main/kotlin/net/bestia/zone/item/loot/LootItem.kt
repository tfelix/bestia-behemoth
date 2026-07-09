package net.bestia.zone.item.loot

import jakarta.persistence.*
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.item.Item

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
  val dropChance: Int  // It is fixed point: 10_000 means 100%, 1000 is 10%, 100 is 1%, 10 is 1 0.1% and 1 is 0.01%

) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  override fun toString(): String {
    return "LootItem(item=${item.id}, chance=${dropChance / 10.0}%)"
  }
}