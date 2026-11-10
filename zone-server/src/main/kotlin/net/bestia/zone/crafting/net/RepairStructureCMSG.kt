package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.RepairStructureCmsgProto
import net.bestia.zone.message.CMSG

data class RepairStructureCMSG(
  override val playerId: Long,
  val entityId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: RepairStructureCmsgProto.RepairStructureCMSG): RepairStructureCMSG {
      return RepairStructureCMSG(playerId = accountId, entityId = msg.entityId)
    }
  }
}
