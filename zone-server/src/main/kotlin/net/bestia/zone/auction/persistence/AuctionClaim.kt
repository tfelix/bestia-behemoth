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
import net.bestia.zone.item.persistence.ItemContainer
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Coins or goods the market owes a master, waiting in the Collect tab until they come for them. Stands in for
 * the postal system, which does not exist yet.
 */
@Entity
@Table(name = "auction_claim", indexes = [Index(columnList = "master_id")])
class AuctionClaim(
  @Column(name = "master_id", nullable = false)
  val masterId: Long,

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false)
  val reason: Reason,

  @Column(nullable = false)
  val coins: Long,

  /** What the claim is about, for the row: the item sold, or the item coming back. */
  @Column(name = "item_id", nullable = false)
  val itemId: Long,

  @Column(nullable = false)
  val amount: Int,

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant,

  /** Empty for a claim of coins only. */
  @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "container_id", nullable = false)
  val container: ItemContainer = ItemContainer(ItemContainer.Type.STORAGE),
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  enum class Reason {
    /** The proceeds of a sale. */
    SOLD,

    /** Goods whose listing ran out unsold. */
    RETURNED,
  }
}
