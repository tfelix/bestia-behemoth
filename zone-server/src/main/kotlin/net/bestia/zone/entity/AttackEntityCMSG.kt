package net.bestia.zone.entity

import net.bestia.bnet.proto.AttackEntityCmsgProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class AttackEntityCMSG(
  override val playerId: Long,
  val targetEntityId: EntityId,
  val usedAttackId: Long,
  val usedSkillLevel: Int
) : CMSG {
  init {
    require(usedSkillLevel >= 1) {
      "usedSkillLevel must be >= 1"
    }

    require(usedAttackId >= 0) {
      "usedAttackId must be >= 0"
    }
  }

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