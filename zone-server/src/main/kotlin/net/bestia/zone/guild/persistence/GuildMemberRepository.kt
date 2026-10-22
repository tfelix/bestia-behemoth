package net.bestia.zone.guild.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** A guild member as the session knows it: which account plays which master. */
data class GuildMemberRef(val accountId: Long, val masterId: Long)

interface GuildMemberRepository : JpaRepository<GuildMember, Long> {

  @Query("select m.guild.id from GuildMember m where m.master.id = :masterId")
  fun findGuildIdOfMaster(@Param("masterId") masterId: Long): Long?

  @Query("select m from GuildMember m where m.master.id = :masterId")
  fun findByMasterId(@Param("masterId") masterId: Long): GuildMember?

  @Query(
    "select new net.bestia.zone.guild.persistence.GuildMemberRef(m.master.account.id, m.master.id) " +
      "from GuildMember m where m.guild.id = :guildId"
  )
  fun findRefsByGuildId(@Param("guildId") guildId: Long): List<GuildMemberRef>

  /** One statement, so a member who left meanwhile is simply not updated. */
  @Modifying
  @Query(
    "update GuildMember m set m.expContributed = m.expContributed + :delta " +
      "where m.guild.id = :guildId and m.master.id = :masterId"
  )
  fun addContribution(
    @Param("guildId") guildId: Long,
    @Param("masterId") masterId: Long,
    @Param("delta") delta: Long,
  ): Int
}
