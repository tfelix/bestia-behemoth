package net.bestia.zone.item

import jakarta.persistence.*
import net.bestia.zone.account.master.Master
import net.bestia.zone.bestia.PlayerBestia

@Entity
@Table(
  name = "inventory_item",
)
class InventoryItem(
  @ManyToOne
  @JoinColumn(name = "master_id", nullable = false)
  val master: Master,

  @ManyToOne
  @JoinColumn(name = "item_id", nullable = false)
  val item: Item,

  var amount: Int = 1
) {

  @ManyToOne
  @JoinColumn(name = "player_bestia_id", nullable = true)
  val playerBestia: PlayerBestia? = null

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}