package net.bestia.zone.item.inventory

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.bestia.zone.account.master.Master
import net.bestia.zone.item.Item

@Entity
@Table(
  name = "player_item",
)
class PlayerItem(
  @ManyToOne
  @JoinColumn(name = "item_id", nullable = false)
  val item: Item,

  val craftedBy: Master? = null,

  val upgradeLevel: Int = 0,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}