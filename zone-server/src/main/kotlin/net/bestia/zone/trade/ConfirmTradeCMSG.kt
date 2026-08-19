package net.bestia.zone.trade

import net.bestia.bnet.proto.ConfirmTradeCmsgProto
import net.bestia.zone.message.CMSG

data class ConfirmTradeCMSG(
  override val playerId: Long,
  val tradeId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: ConfirmTradeCmsgProto.ConfirmTradeCMSG): ConfirmTradeCMSG {
      return ConfirmTradeCMSG(playerId = accountId, tradeId = msg.tradeId)
    }
  }
}
