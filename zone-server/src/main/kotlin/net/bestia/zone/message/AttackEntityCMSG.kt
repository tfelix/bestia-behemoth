package net.bestia.zone.message

import net.bestia.bnet.proto.AttackEntityCmsgProto
import net.bestia.zone.util.EntityId

data class AttackEntityCMSG(
  override val playerId: Long,
  val targetEntityId: EntityId,
  val usedAttackId: Long,
  val usedSkillLevel: Int
) : CMSG {
  companion object {
    fun fromBnet(
      accountId: Long,
      attackEntity: AttackEntityCmsgProto.AttackEntityCMSG
    ): AttackEntityCMSG {
      return AttackEntityCMSG(
        accountId,
        attackEntity.entityId,
        attackEntity.usedAttackId,
        attackEntity.skillLevel
      )
    }
  }
}
