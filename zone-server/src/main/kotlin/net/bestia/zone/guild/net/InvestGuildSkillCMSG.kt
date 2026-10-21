package net.bestia.zone.guild.net

import net.bestia.bnet.proto.InvestGuildSkillCmsgProto
import net.bestia.zone.message.CMSG

data class InvestGuildSkillCMSG(
  override val playerId: Long,
  /** Null for a skill this server does not know. */
  val skill: GuildSkill?,
) : CMSG {

  enum class GuildSkill { EXTENSION }

  companion object {
    fun fromBnet(accountId: Long, proto: InvestGuildSkillCmsgProto.InvestGuildSkillCMSG): InvestGuildSkillCMSG {
      val skill = when (proto.skill) {
        InvestGuildSkillCmsgProto.GuildSkill.GUILD_SKILL_EXTENSION -> GuildSkill.EXTENSION
        else -> null
      }

      return InvestGuildSkillCMSG(playerId = accountId, skill = skill)
    }
  }
}
