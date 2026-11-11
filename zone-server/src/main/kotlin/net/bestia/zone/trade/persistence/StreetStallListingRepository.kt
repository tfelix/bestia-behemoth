package net.bestia.zone.trade.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface StreetStallListingRepository : JpaRepository<StreetStallListing, Long> {

  fun findByStallEntityId(stallEntityId: Long): List<StreetStallListing>

  fun countByStallEntityId(stallEntityId: Long): Long

  /** Locked, so two buyers of one listing are served one after the other and the second finds it gone. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT l FROM StreetStallListing l WHERE l.id = :id")
  fun findLockedById(id: Long): StreetStallListing?
}
