package net.bestia.zone.auction.persistence

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
import java.time.Instant

/**
 * Goods bought or won from a linked house, waiting at that house for their buyer: to be collected in person or
 * sent by post. Unclaimed when [expiresAt] comes, they go back to the seller, who keeps the payment.
 */
@Entity
@Table(name = "auction_pickup", indexes = [Index(columnList = "buyer_master_id")])
class AuctionPickup(
  @Column(name = "buyer_master_id", nullable = false)
  val buyerMasterId: Long,

  @Column(name = "seller_master_id", nullable = false)
  val sellerMasterId: Long,

  /** The player house it waits at. */
  @Column(name = "house_structure_id", nullable = false)
  val houseStructureId: Long,

  @Column(name = "town_name", nullable = false)
  val townName: String,

  @Column(name = "expires_at", nullable = false)
  val expiresAt: Instant,

  @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "container_id", nullable = false)
  val container: ItemContainer,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
