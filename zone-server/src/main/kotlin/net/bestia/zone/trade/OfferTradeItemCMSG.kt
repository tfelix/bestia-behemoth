package net.bestia.zone.trade

import net.bestia.bnet.proto.OfferTradeItemCmsgProto
import net.bestia.zone.message.CMSG

data class OfferTradeItemCMSG(
  override val playerId: Long,
  val tradeId: Long,
  val itemId: Long,
  val uniqueId: Long,
  val amount: Int,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: OfferTradeItemCmsgProto.OfferTradeItemCMSG): OfferTradeItemCMSG {
      return OfferTradeItemCMSG(
        playerId = accountId,
        tradeId = msg.tradeId,
        itemId = msg.itemId.toLong(),
        uniqueId = msg.uniqueId,
        amount = msg.amount
      )
    }
  }
}
