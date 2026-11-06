package net.bestia.zone.postal.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ParcelRepository : JpaRepository<Parcel, Long> {

  fun findByRecipientMasterIdOrderByLatestArrival(recipientMasterId: Long): List<Parcel>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Parcel p where p.id = :id")
  fun findLockedById(@Param("id") id: Long): Parcel?

  fun findByAnnouncedFalseAndLatestArrivalLessThanEqual(now: Instant): List<Parcel>

  fun findByReturningFalseAndLatestArrivalLessThan(before: Instant): List<Parcel>

  fun findByOriginOfficeAndSackInstanceIdIsNullOrderByLatestArrival(originOffice: Int): List<Parcel>

  fun findByCourierMasterIdAndDestinationOffice(courierMasterId: Long, destinationOffice: Int): List<Parcel>

  fun findByCourierMasterIdIsNotNullAndLatestArrivalLessThanEqual(now: Instant): List<Parcel>

  fun findBySackInstanceId(sackInstanceId: Long): List<Parcel>
}
