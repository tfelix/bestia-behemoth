package net.bestia.zone.guild.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GuildRepository : JpaRepository<Guild, Long> {

  /**
   * Holds the guild row until the transaction ends. Every change to a guild takes it first, so two officers
   * editing at once, or an EXP flush landing mid-edit, see each other's work rather than overwriting it.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from Guild g where g.id = :id")
  fun findByIdForUpdate(@Param("id") id: Long): Guild?

  @Query("select g.name from Guild g where g.leader.id = :masterId")
  fun findNameLedBy(@Param("masterId") masterId: Long): String?
}
