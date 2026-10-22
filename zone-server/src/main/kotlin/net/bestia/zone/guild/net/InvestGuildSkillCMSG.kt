package net.bestia.zone.guild.net

import net.bestia.bnet.proto.InvestGuildSkillCmsgProto
import net.bestia.zone.message.CMSG

data class InvestGuildSkillCMSG(
  override val playerId: Long,
  val skillId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: InvestGuildSkillCmsgProto.InvestGuildSkillCMSG): InvestGuildSkillCMSG {
      return InvestGuildSkillCMSG(playerId = accountId, skillId = proto.skillId)
    }
  }
}
