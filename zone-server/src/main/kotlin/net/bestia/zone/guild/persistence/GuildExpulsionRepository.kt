package net.bestia.zone.guild.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GuildExpulsionRepository : JpaRepository<GuildExpulsion, Long> {

  @Query("select e from GuildExpulsion e where e.guild.id = :guildId and e.masterId = :masterId")
  fun find(@Param("guildId") guildId: Long, @Param("masterId") masterId: Long): GuildExpulsion?

  @Query("select e from GuildExpulsion e where e.guild.id = :guildId order by e.at desc")
  fun findNewest(@Param("guildId") guildId: Long): List<GuildExpulsion>

  @Modifying
  @Query("delete from GuildExpulsion e where e.guild.id = :guildId")
  fun deleteByGuildId(@Param("guildId") guildId: Long): Int
}
