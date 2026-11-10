package net.bestia.zone.settlement.net

import net.bestia.bnet.proto.PlanSettlementCmsgProto
import net.bestia.zone.message.CMSG

data class PlanSettlementCMSG(
  override val playerId: Long,
  val name: String,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: PlanSettlementCmsgProto.PlanSettlementCMSG): PlanSettlementCMSG {
      return PlanSettlementCMSG(playerId = accountId, name = msg.name)
    }
  }
}
