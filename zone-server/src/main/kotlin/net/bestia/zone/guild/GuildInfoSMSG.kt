package net.bestia.zone.guild

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.GuildInfoSmsgProto
import net.bestia.zone.message.SMSG

/** The whole guild as one member sees it. [guildId] 0 with no members is "in no guild", see [none]. */
data class GuildInfoSMSG(
  val guildId: Long,
  val name: String,
  val level: Int,
  val exp: Long,
  val expNextLevel: Long,
  val skillPoints: Int,
  val maxMembers: Int,
  val ranks: List<Rank>,
  val members: List<Member>,
  val ownMasterId: Long,
  val maxRanks: Int,
  val minTaxPercent: Int,
  val maxTaxPercent: Int,
  val skills: List<Skill>,
) : SMSG {

  data class Rank(
    val rankId: Long,
    val position: Int,
    val name: String,
    val taxPercent: Int,
    val canInvite: Boolean,
    val canKick: Boolean,
    val canManageRanks: Boolean,
    val canUseGuildSkills: Boolean,
  )

  data class Skill(
    val skillId: Long,
    val level: Int,
    val maxLevel: Int,
    val prerequisites: List<Prerequisite>,
  )

  data class Prerequisite(val skillId: Long, val level: Int)

  data class Member(
    val masterId: Long,
    val masterName: String,
    val level: Int,
    val rankId: Long,
    val online: Boolean,
    val expContributed: Long,
    val isLeader: Boolean,
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = GuildInfoSmsgProto.GuildInfoSMSG.newBuilder()
      .setGuildId(guildId)
      .setName(name)
      .setLevel(level)
      .setExp(exp)
      .setExpNextLevel(expNextLevel)
      .setSkillPoints(skillPoints)
      .setMaxMembers(maxMembers)
      .addAllRanks(ranks.map { rank ->
        GuildInfoSmsgProto.GuildRank.newBuilder()
          .setRankId(rank.rankId)
          .setPosition(rank.position)
          .setName(rank.name)
          .setTaxPercent(rank.taxPercent)
          .setCanInvite(rank.canInvite)
          .setCanKick(rank.canKick)
          .setCanManageRanks(rank.canManageRanks)
          .setCanUseGuildSkills(rank.canUseGuildSkills)
          .build()
      })
      .addAllMembers(members.map { member ->
        GuildInfoSmsgProto.GuildMember.newBuilder()
          .setMasterId(member.masterId)
          .setMasterName(member.masterName)
          .setLevel(member.level)
          .setRankId(member.rankId)
          .setOnline(member.online)
          .setExpContributed(member.expContributed)
          .setIsLeader(member.isLeader)
          .build()
      })
      .setOwnMasterId(ownMasterId)
      .setMaxRanks(maxRanks)
      .setMinTaxPercent(minTaxPercent)
      .setMaxTaxPercent(maxTaxPercent)
      .addAllSkills(skills.map { skill ->
        GuildInfoSmsgProto.GuildSkillNode.newBuilder()
          .setSkillId(skill.skillId)
          .setLevel(skill.level)
          .setMaxLevel(skill.maxLevel)
          .addAllPrerequisites(skill.prerequisites.map { prerequisite ->
            GuildInfoSmsgProto.GuildSkillPrerequisite.newBuilder()
              .setSkillId(prerequisite.skillId)
              .setLevel(prerequisite.level)
              .build()
          })
          .build()
      })
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setGuildInfo(proto)
      .build()
  }

  companion object {
    /** The answer for a master in no guild. */
    fun none(ownMasterId: Long) = GuildInfoSMSG(
      guildId = 0,
      name = "",
      level = 0,
      exp = 0,
      expNextLevel = 0,
      skillPoints = 0,
      maxMembers = 0,
      ranks = emptyList(),
      members = emptyList(),
      ownMasterId = ownMasterId,
      maxRanks = 0,
      minTaxPercent = 0,
      maxTaxPercent = 0,
      skills = emptyList(),
    )
  }
}
