package net.bestia.zone.trade.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.bestia.zone.item.persistence.ItemContainer

/**
 * One offer on a Trader's street stall. The goods sit in [container], held by no player, so a crash while the
 * stall stands loses nothing: the boot posts every listing left over back to its seller.
 *
 * [stallEntityId] names the live stall entity. A stall is never persisted itself, it only lives for an hour.
 */
@Entity
@Table(name = "street_stall_listing", indexes = [Index(columnList = "stall_entity_id")])
class StreetStallListing(
  @Column(name = "stall_entity_id", nullable = false)
  val stallEntityId: Long,

  @Column(name = "seller_master_id", nullable = false)
  val sellerMasterId: Long,

  @Column(name = "item_id", nullable = false)
  val itemId: Long,

  @Column(nullable = false)
  val amount: Int,

  @Column(name = "item_level", nullable = false)
  val itemLevel: Int,

  /** For the whole [amount]. */
  @Column(nullable = false)
  val price: Long,

  @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "container_id", nullable = false)
  val container: ItemContainer,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
