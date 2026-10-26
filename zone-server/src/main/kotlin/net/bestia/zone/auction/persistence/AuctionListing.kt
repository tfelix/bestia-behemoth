package net.bestia.zone.auction.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.bestia.zone.auction.AuctionCategory
import net.bestia.zone.item.persistence.ItemContainer
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Goods up for sale in the shared market. The goods themselves sit in [container], held by no player, so a
 * listing survives its seller logging off and the boot that releases stale trade reservations.
 *
 * Not tied to a world: the market outlives a regenerated map, and its houses are found again by town.
 */
@Entity
@Table(
  name = "auction_listing",
  indexes = [Index(columnList = "category"), Index(columnList = "seller_master_id")]
)
class AuctionListing(
  @Column(name = "seller_master_id", nullable = false)
  val sellerMasterId: Long,

  /** A snapshot, so a deleted master still has a name on the board. */
  @Column(name = "seller_name", nullable = false)
  val sellerName: String,

  @Column(name = "item_id", nullable = false)
  val itemId: Long,

  @Column(nullable = false)
  val amount: Int,

  @Column(name = "item_level", nullable = false)
  val itemLevel: Int,

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false)
  val category: AuctionCategory,

  /** Coins for the whole lot. */
  @Column(nullable = false)
  val price: Long,

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
