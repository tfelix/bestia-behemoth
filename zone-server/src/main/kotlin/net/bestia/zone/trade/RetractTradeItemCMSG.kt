package net.bestia.zone.trade

import net.bestia.bnet.proto.RetractTradeItemCmsgProto
import net.bestia.zone.message.CMSG

data class RetractTradeItemCMSG(
  override val playerId: Long,
  val tradeId: Long,
  val offerSlotId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: RetractTradeItemCmsgProto.RetractTradeItemCMSG): RetractTradeItemCMSG {
      return RetractTradeItemCMSG(
        playerId = accountId,
        tradeId = msg.tradeId,
        offerSlotId = msg.offerSlotId
      )
    }
  }
}
