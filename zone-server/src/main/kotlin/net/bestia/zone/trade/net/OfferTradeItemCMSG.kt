package net.bestia.zone.trade.net

import net.bestia.bnet.proto.OfferTradeItemCmsgProto
import net.bestia.zone.message.CMSG

data class OfferTradeItemCMSG(
  override val playerId: Long,
  val tradeId: Long,
  val playerItemId: Long,
  val amount: Int,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: OfferTradeItemCmsgProto.OfferTradeItemCMSG): OfferTradeItemCMSG {
      return OfferTradeItemCMSG(
        playerId = accountId,
        tradeId = msg.tradeId,
        playerItemId = msg.playerItemId,
        amount = msg.amount
      )
    }
  }
}
