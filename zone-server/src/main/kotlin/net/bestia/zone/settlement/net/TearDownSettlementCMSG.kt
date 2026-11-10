package net.bestia.zone.settlement.net

import net.bestia.bnet.proto.TearDownSettlementCmsgProto
import net.bestia.zone.message.CMSG

data class TearDownSettlementCMSG(
  override val playerId: Long,
  val signEntityId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: TearDownSettlementCmsgProto.TearDownSettlementCMSG): TearDownSettlementCMSG {
      return TearDownSettlementCMSG(playerId = accountId, signEntityId = msg.signEntityId)
    }
  }
}
