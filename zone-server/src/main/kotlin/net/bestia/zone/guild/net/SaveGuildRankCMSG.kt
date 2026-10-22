package net.bestia.zone.guild.net

import net.bestia.bnet.proto.SaveGuildRankCmsgProto
import net.bestia.zone.message.CMSG

data class SaveGuildRankCMSG(
  override val playerId: Long,
  /** 0 adds a new rank. */
  val rankId: Long,
  val name: String,
  val taxPercent: Int,
  val canInvite: Boolean,
  val canKick: Boolean,
  val canManageRanks: Boolean,
  val canUseGuildSkills: Boolean,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: SaveGuildRankCmsgProto.SaveGuildRankCMSG): SaveGuildRankCMSG {
      return SaveGuildRankCMSG(
        playerId = accountId,
        rankId = proto.rankId,
        name = proto.name,
        taxPercent = proto.taxPercent,
        canInvite = proto.canInvite,
        canKick = proto.canKick,
        canManageRanks = proto.canManageRanks,
        canUseGuildSkills = proto.canUseGuildSkills,
      )
    }
  }
}
