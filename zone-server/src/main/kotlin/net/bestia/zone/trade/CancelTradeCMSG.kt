package net.bestia.zone.trade

import net.bestia.bnet.proto.CancelTradeCmsgProto
import net.bestia.zone.message.CMSG

data class CancelTradeCMSG(
  override val playerId: Long,
  val tradeId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: CancelTradeCmsgProto.CancelTradeCMSG): CancelTradeCMSG {
      return CancelTradeCMSG(playerId = accountId, tradeId = msg.tradeId)
    }
  }
}
