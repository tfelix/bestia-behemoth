package net.bestia.zone.auction.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * An auction house a master built with Trade Post Owner, named by its `player_structure` row. See the design
 * docs' economy page, "Building an Auction House".
 */
@Entity
@Table(name = "player_auction_house")
class PlayerAuctionHouse(
  @Id
  @Column(name = "structure_id")
  val structureId: Long,

  @Column(name = "owner_master_id", nullable = false)
  val ownerMasterId: Long,

  @Column(name = "owner_account_id", nullable = false)
  val ownerAccountId: Long,

  /** A snapshot, so the window names its owner without a lookup. */
  @Column(name = "owner_name", nullable = false)
  val ownerName: String,

  /** Charged on every new listing, on top of the listing fee. */
  @Column(name = "fee_percent", nullable = false)
  var feePercent: Int,

  @Column(name = "fee_changed_at")
  var feeChangedAt: Instant?,

  /** The owner's half of the owner fees, waiting to be collected in person. */
  @Column(nullable = false)
  var till: Long = 0,
)
