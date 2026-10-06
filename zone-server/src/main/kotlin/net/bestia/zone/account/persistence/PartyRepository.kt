package net.bestia.zone.account.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PartyRepository : JpaRepository<Party, Long> {

  fun findByOwner(owner: Master): Party?


  fun findByMember(master: Master): Party?

  /** Holds the party row until the transaction ends, so a change that counts its members sees every other one. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Party p where p.id = :id")
  fun findByIdForUpdate(@Param("id") id: Long): Party?
}
