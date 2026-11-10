package net.bestia.zone.auction.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface AuctionPickupRepository : JpaRepository<AuctionPickup, Long> {

  fun findByBuyerMasterIdOrderByExpiresAt(buyerMasterId: Long): List<AuctionPickup>

  fun findByExpiresAtBefore(now: Instant): List<AuctionPickup>

  fun findByHouseStructureId(houseStructureId: Long): List<AuctionPickup>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM AuctionPickup p WHERE p.id = :id")
  fun findLockedById(id: Long): AuctionPickup?
}
