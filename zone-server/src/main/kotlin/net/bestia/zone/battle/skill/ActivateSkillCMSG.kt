package net.bestia.zone.battle.skill

import net.bestia.bnet.proto.ActivateSkillCmsgProto
import net.bestia.zone.message.CMSG

data class ActivateSkillCMSG(
  override val playerId: Long,
  val attackId: Long,
  val skillLevel: Int
) : CMSG {
  companion object {
    fun fromBnet(
      accountId: Long,
      activateSkill: ActivateSkillCmsgProto.ActivateSkillCMSG
    ): ActivateSkillCMSG {
      return ActivateSkillCMSG(
        accountId,
        activateSkill.attackId,
        activateSkill.skillLevel
      )
    }
  }
}
