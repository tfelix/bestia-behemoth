package net.bestia.zone.guild

import net.bestia.zone.guild.persistence.GuildMemberRef
import net.bestia.zone.guild.persistence.GuildMemberRepository
import net.bestia.zone.guild.persistence.GuildRepository
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * A guild as plain values, read in one transaction so [GuildInfoPublisher] can send it after the transaction
 * has ended. No world read: a member's level comes off the master row, which the persister keeps current.
 */
@Component
class GuildInfoReader(
  private val guildRepository: GuildRepository,
  private val memberRepository: GuildMemberRepository,
  private val sessions: ConnectionInfoService,
  private val levels: GuildLevels,
  private val config: GuildConfig,
) {

  /** [info] with no recipient set yet, and every member to send it to. */
  data class Snapshot(val info: GuildInfoSMSG, val members: List<GuildMemberRef>)

  @Transactional(readOnly = true)
  fun read(guildId: Long): Snapshot? {
    val guild = guildRepository.findByIdOrNull(guildId) ?: return null

    val ranks = guild.ranks.sortedBy { it.position }
    val positions = ranks.associate { it.id to it.position }
    val members = guild.members.sortedWith(
      compareBy({ positions[it.rank.id] ?: Int.MAX_VALUE }, { it.master.name })
    )

    val info = GuildInfoSMSG(
      guildId = guild.id,
      name = guild.name,
      level = guild.level,
      exp = guild.exp,
      expNextLevel = levels.requiredExp(guild.level),
      skillPoints = levels.skillPoints(guild.level, guild.extensionLevel),
      extensionLevel = guild.extensionLevel,
      maxMembers = levels.maxMembers(guild.extensionLevel),
      ranks = ranks.map { rank ->
        GuildInfoSMSG.Rank(
          rankId = rank.id,
          position = rank.position,
          name = rank.name,
          taxPercent = rank.taxPercent,
          canInvite = rank.canInvite,
          canKick = rank.canKick,
          canManageRanks = rank.canManageRanks,
        )
      },
      members = members.map { member ->
        GuildInfoSMSG.Member(
          masterId = member.master.id,
          masterName = member.master.name,
          level = member.master.level,
          rankId = member.rank.id,
          online = sessions.isPlaying(member.master.account.id, member.master.id),
          expContributed = member.expContributed,
          isLeader = member.isLeader,
        )
      },
      ownMasterId = 0,
      maxExtensionLevel = config.maxExtensionLevel,
      maxRanks = config.maxRanks,
      minTaxPercent = config.minTaxPercent,
      maxTaxPercent = config.maxTaxPercent,
    )

    return Snapshot(info, members.map { GuildMemberRef(it.master.account.id, it.master.id) })
  }

  @Transactional(readOnly = true)
  fun guildIdOfMaster(masterId: Long): Long? {
    return memberRepository.findGuildIdOfMaster(masterId)
  }
}
