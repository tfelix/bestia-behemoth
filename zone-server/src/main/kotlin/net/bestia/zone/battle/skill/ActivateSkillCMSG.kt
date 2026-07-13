package net.bestia.zone.battle.skill

import net.bestia.bnet.proto.ActivateSkillCmsgProto
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.CMSG

data class ActivateSkillCMSG(
  override val playerId: Long,
  val attackId: Long,
  val skillLevel: Int,
  val targetPosition: Vec3L,
  val targetEntityId: Long
) : CMSG {
  companion object {
    fun fromBnet(
      accountId: Long,
      activateSkill: ActivateSkillCmsgProto.ActivateSkillCMSG
    ): ActivateSkillCMSG {
      return ActivateSkillCMSG(
        accountId,
        activateSkill.attackId,
        activateSkill.skillLevel,
        Vec3L(activateSkill.targetPosition.x, activateSkill.targetPosition.y, activateSkill.targetPosition.z),
        activateSkill.targetEntityId
      )
    }
  }
}
