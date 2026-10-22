package net.bestia.zone.guild

import net.bestia.zone.guild.persistence.GuildMemberRef
import net.bestia.zone.guild.persistence.GuildMemberRepository
import net.bestia.zone.guild.persistence.GuildRepository
import net.bestia.zone.guild.skill.GuildSkillCooldowns
import net.bestia.zone.guild.skill.GuildSkillTree
import net.bestia.zone.place.ecs.PlaceNameService
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * A guild as plain values, read in one transaction so [GuildInfoPublisher] can send it after the transaction
 * has ended. No world read: a member's level and last whereabouts come off the master row, which the persister
 * keeps current.
 */
@Component
class GuildInfoReader(
  private val guildRepository: GuildRepository,
  private val memberRepository: GuildMemberRepository,
  private val sessions: ConnectionInfoService,
  private val levels: GuildLevels,
  private val config: GuildConfig,
  private val skillTree: GuildSkillTree,
  private val cooldowns: GuildSkillCooldowns,
  private val placeNames: PlaceNameService,
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
      skillPoints = levels.skillPoints(guild.level, guild.spentSkillPoints),
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
          canUseGuildSkills = rank.canUseGuildSkills,
          canEditMotd = rank.canEditMotd,
        )
      },
      members = members.map { member ->
        val master = member.master
        val online = sessions.isPlaying(master.account.id, master.id)

        GuildInfoSMSG.Member(
          masterId = master.id,
          masterName = master.name,
          level = master.level,
          rankId = member.rank.id,
          online = online,
          expContributed = member.expContributed,
          isLeader = member.isLeader,
          entityId = master.entityId,
          lastAreaName = if (online) "" else placeNames.resolve(master.currentPosition.x, master.currentPosition.y).name,
          lastSeenEpochMillis = master.persistedAtEpochMs,
        )
      },
      ownMasterId = 0,
      maxRanks = config.maxRanks,
      minTaxPercent = config.minTaxPercent,
      maxTaxPercent = config.maxTaxPercent,
      skills = skillTree.all().map { node ->
        GuildInfoSMSG.Skill(
          skillId = node.skillId,
          level = guild.skillLevel(node.identifier),
          maxLevel = node.maxLevel,
          prerequisites = node.prerequisites.map { GuildInfoSMSG.Prerequisite(it.skillId, it.level) },
          cooldownRemainingMillis = cooldowns.remainingMillis(guild.id, node.skillId),
        )
      },
      motdTitle = guild.motdTitle,
      motdBody = guild.motdBody,
      motdTitleMaxLength = config.motdTitleMaxLength,
      motdBodyMaxLength = config.motdBodyMaxLength,
    )

    return Snapshot(info, members.map { GuildMemberRef(it.master.account.id, it.master.id) })
  }

  @Transactional(readOnly = true)
  fun guildIdOfMaster(masterId: Long): Long? {
    return memberRepository.findGuildIdOfMaster(masterId)
  }
}
