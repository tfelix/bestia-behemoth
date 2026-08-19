package net.bestia.zone.trade

import net.bestia.bnet.proto.AnswerTradeRequestCmsgProto
import net.bestia.zone.message.CMSG

data class AnswerTradeRequestCMSG(
  override val playerId: Long,
  val tradeId: Long,
  val accept: Boolean,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: AnswerTradeRequestCmsgProto.AnswerTradeRequestCMSG): AnswerTradeRequestCMSG {
      return AnswerTradeRequestCMSG(
        playerId = accountId,
        tradeId = msg.tradeId,
        accept = msg.accept
      )
    }
  }
}
