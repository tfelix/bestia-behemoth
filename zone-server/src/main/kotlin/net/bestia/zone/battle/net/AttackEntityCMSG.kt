package net.bestia.zone.battle.net

import net.bestia.bnet.proto.AttackEntityCmsgProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

/**
 * A swing of the active entity's basic attack at [targetEntityId]. Carries no attack id and no level:
 * a basic attack has no catalogue row, and a skill is cast with [net.bestia.zone.casting.net.ActivateSkillCMSG].
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
