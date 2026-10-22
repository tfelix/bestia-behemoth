package net.bestia.zone.guild.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GuildLogEntryRepository : JpaRepository<GuildLogEntry, Long> {

  @Query("select e from GuildLogEntry e where e.guild.id = :guildId order by e.id desc")
  fun findNewest(@Param("guildId") guildId: Long, page: Pageable): List<GuildLogEntry>

  @Query("select e.id from GuildLogEntry e where e.guild.id = :guildId order by e.id desc")
  fun findNewestIds(@Param("guildId") guildId: Long, page: Pageable): List<Long>

  @Modifying
  @Query("delete from GuildLogEntry e where e.guild.id = :guildId and e.id < :id")
  fun deleteOlderThan(@Param("guildId") guildId: Long, @Param("id") id: Long): Int

  @Modifying
  @Query("delete from GuildLogEntry e where e.guild.id = :guildId")
  fun deleteByGuildId(@Param("guildId") guildId: Long): Int
}
