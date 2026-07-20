package net.bestia.zone.account.master

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MasterRepository : JpaRepository<Master, Long> {
  fun findByName(name: String): Master?

  /**
   * Same lookup as [findByIdOrNull], but takes a `SELECT ... FOR UPDATE` row lock for the rest of
   * the enclosing transaction. Use this instead of a plain find whenever the caller does a
   * read-modify-write against mutable master state (e.g. accumulating exp) that can be reached by
   * more than one concurrently-running job for the same master id - otherwise two such jobs can
   * each read the same starting value and one write clobbers the other (lost update).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Master m where m.id = :id")
  fun findByIdForUpdate(@Param("id") id: Long): Master?
}

fun MasterRepository.findByIdOrThrow(id: Long): Master {
  return findByIdOrNull(id) ?: throw MasterNotFoundException()
}