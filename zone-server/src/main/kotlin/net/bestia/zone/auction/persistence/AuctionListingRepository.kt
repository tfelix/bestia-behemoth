package net.bestia.zone.auction.persistence

import net.bestia.zone.auction.AuctionCategory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface AuctionListingRepository : JpaRepository<AuctionListing, Long> {

  fun countBySellerMasterId(sellerMasterId: Long): Long

  /** Locked, so two buyers of one listing are served one after the other and the second finds it gone. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT l FROM AuctionListing l WHERE l.id = :id")
  fun findLockedById(id: Long): AuctionListing?

  /**
   * Cheapest per unit first. [itemIds] must not be empty - JPQL has no empty `IN` - so "any item" is
   * [anyItem] rather than an empty list.
   */
  @Query(
    value = """
      SELECT l FROM AuctionListing l
      WHERE (:category IS NULL OR l.category = :category)
        AND l.itemLevel BETWEEN :minLevel AND :maxLevel
        AND (:anyItem = true OR l.itemId IN :itemIds)
        AND (:sellerMasterId IS NULL OR l.sellerMasterId = :sellerMasterId)
      ORDER BY (l.price * 1.0 / l.amount), l.id
    """,
    countQuery = """
      SELECT COUNT(l) FROM AuctionListing l
      WHERE (:category IS NULL OR l.category = :category)
        AND l.itemLevel BETWEEN :minLevel AND :maxLevel
        AND (:anyItem = true OR l.itemId IN :itemIds)
        AND (:sellerMasterId IS NULL OR l.sellerMasterId = :sellerMasterId)
    """
  )
  fun search(
    category: AuctionCategory?,
    minLevel: Int,
    maxLevel: Int,
    anyItem: Boolean,
    itemIds: Collection<Long>,
    sellerMasterId: Long?,
    pageable: Pageable,
  ): Page<AuctionListing>
}
