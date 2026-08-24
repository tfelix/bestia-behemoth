package net.bestia.zone.battle

import net.bestia.bnet.proto.AttackEntityCmsgProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

/**
 * A swing of the active entity's basic attack at [targetEntityId]. Carries no attack id and no level:
 * a basic attack has no catalogue row, and a skill is cast with [ActivateSkillCMSG].
 */
data class AttackEntityCMSG(
  override val playerId: Long,
  val targetEntityId: EntityId,
) : CMSG {

  companion object {
    fun fromBnet(
      accountId: Long,
      attackEntity: AttackEntityCmsgProto.AttackEntityCMSG
    ): AttackEntityCMSG {
      return AttackEntityCMSG(
        accountId,
        attackEntity.entityId
      )
    }
  }
}
