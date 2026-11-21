package net.bestia.zone.item.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GuildStorageRepository : JpaRepository<GuildStorage, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from GuildStorage s where s.guildId = :guildId")
  fun findByIdForUpdate(@Param("guildId") guildId: Long): GuildStorage?
}
