package net.bestia.zone.item.inventory

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
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
  @JoinColumn(name = "player_item_id", nullable = false)
  val playerItem: PlayerItem,

  var amount: Int = 1
) {

  /**
   * Optional if the item is associated with a player bestia. If it is inside a master
   * inventory this here is null.
   */
  @ManyToOne
  @JoinColumn(name = "player_bestia_id", nullable = true)
  val playerBestia: PlayerBestia? = null

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
