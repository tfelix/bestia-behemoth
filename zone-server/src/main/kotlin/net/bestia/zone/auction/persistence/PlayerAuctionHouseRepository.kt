package net.bestia.zone.auction.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface PlayerAuctionHouseRepository : JpaRepository<PlayerAuctionHouse, Long> {

  fun findByOwnerMasterId(ownerMasterId: Long): List<PlayerAuctionHouse>

  fun countByOwnerMasterId(ownerMasterId: Long): Long

  /** Locked, so the till is added to and emptied one at a time. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT h FROM PlayerAuctionHouse h WHERE h.structureId = :structureId")
  fun findLockedById(structureId: Long): PlayerAuctionHouse?
}
