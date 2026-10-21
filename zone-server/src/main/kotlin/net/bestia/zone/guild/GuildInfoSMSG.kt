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
  val extensionLevel: Int,
  val maxMembers: Int,
  val ranks: List<Rank>,
  val members: List<Member>,
  val ownMasterId: Long,
  val maxExtensionLevel: Int,
  val maxRanks: Int,
  val minTaxPercent: Int,
  val maxTaxPercent: Int,
) : SMSG {

  data class Rank(
    val rankId: Long,
    val position: Int,
    val name: String,
    val taxPercent: Int,
    val canInvite: Boolean,
    val canKick: Boolean,
    val canManageRanks: Boolean,
  )

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
      .setExtensionLevel(extensionLevel)
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
      .setMaxExtensionLevel(maxExtensionLevel)
      .setMaxRanks(maxRanks)
      .setMinTaxPercent(minTaxPercent)
      .setMaxTaxPercent(maxTaxPercent)
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
      extensionLevel = 0,
      maxMembers = 0,
      ranks = emptyList(),
      members = emptyList(),
      ownMasterId = ownMasterId,
      maxExtensionLevel = 0,
      maxRanks = 0,
      minTaxPercent = 0,
      maxTaxPercent = 0,
    )
  }
}
