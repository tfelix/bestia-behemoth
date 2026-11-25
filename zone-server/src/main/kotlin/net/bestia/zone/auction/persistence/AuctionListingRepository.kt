package net.bestia.zone.auction.persistence

import net.bestia.zone.auction.AuctionCategory
import net.bestia.zone.item.essence.GemTier
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface AuctionListingRepository : JpaRepository<AuctionListing, Long> {

  fun countBySellerMasterId(sellerMasterId: Long): Long

  fun findByExpiresAtBefore(now: Instant): List<AuctionListing>

  fun findByHouseStructureId(houseStructureId: Long): List<AuctionListing>

  fun countByHouseStructureId(houseStructureId: Long): Long

  /** Locked, so two buyers of one listing are served one after the other and the second finds it gone. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT l FROM AuctionListing l WHERE l.id = :id")
  fun findLockedById(id: Long): AuctionListing?

  /**
   * Cheapest per unit first. [itemIds] must not be empty - JPQL has no empty `IN` - so "any item" is
   * [anyItem] rather than an empty list; [essenceBestiaIds] and [anyEssenceSpecies] the same. With [withEssence],
   * only goods with an essence socketed into them or held in an essence gem.
   */
  @Query(
    value = """
      SELECT l FROM AuctionListing l
      WHERE (:category IS NULL OR l.category = :category)
        AND l.itemLevel BETWEEN :minLevel AND :maxLevel
        AND (:anyItem = true OR l.itemId IN :itemIds)
        AND (:sellerMasterId IS NULL OR l.sellerMasterId = :sellerMasterId)
        AND (:withEssence = false OR EXISTS (
          SELECT s.id FROM ContainerSlot s JOIN s.itemInstance i LEFT JOIN i.socketRows e
          WHERE s.container = l.container AND (
            (i.heldEssence.bestiaId IS NOT NULL
              AND (:anyEssenceSpecies = true OR i.heldEssence.bestiaId IN :essenceBestiaIds)
              AND (:essenceTier IS NULL OR i.heldEssence.tier = :essenceTier))
            OR (e.bestiaId IS NOT NULL
              AND (:anyEssenceSpecies = true OR e.bestiaId IN :essenceBestiaIds)
              AND (:essenceTier IS NULL OR e.tier = :essenceTier))
          )
        ))
        AND l.expiresAt > :now
        AND ((:startTowns = true AND l.houseStructureId IS NULL) OR l.houseStructureId IN :houseIds)
      ORDER BY (l.price * 1.0 / l.amount), l.id
    """,
    countQuery = """
      SELECT COUNT(l) FROM AuctionListing l
      WHERE (:category IS NULL OR l.category = :category)
        AND l.itemLevel BETWEEN :minLevel AND :maxLevel
        AND (:anyItem = true OR l.itemId IN :itemIds)
        AND (:sellerMasterId IS NULL OR l.sellerMasterId = :sellerMasterId)
        AND (:withEssence = false OR EXISTS (
          SELECT s.id FROM ContainerSlot s JOIN s.itemInstance i LEFT JOIN i.socketRows e
          WHERE s.container = l.container AND (
            (i.heldEssence.bestiaId IS NOT NULL
              AND (:anyEssenceSpecies = true OR i.heldEssence.bestiaId IN :essenceBestiaIds)
              AND (:essenceTier IS NULL OR i.heldEssence.tier = :essenceTier))
            OR (e.bestiaId IS NOT NULL
              AND (:anyEssenceSpecies = true OR e.bestiaId IN :essenceBestiaIds)
              AND (:essenceTier IS NULL OR e.tier = :essenceTier))
          )
        ))
        AND l.expiresAt > :now
        AND ((:startTowns = true AND l.houseStructureId IS NULL) OR l.houseStructureId IN :houseIds)
    """
  )
  fun search(
    category: AuctionCategory?,
    minLevel: Int,
    maxLevel: Int,
    anyItem: Boolean,
    itemIds: Collection<Long>,
    sellerMasterId: Long?,
    withEssence: Boolean,
    anyEssenceSpecies: Boolean,
    essenceBestiaIds: Collection<Long>,
    essenceTier: GemTier?,
    now: Instant,
    startTowns: Boolean,
    houseIds: Collection<Long>,
    pageable: Pageable,
  ): Page<AuctionListing>
}
