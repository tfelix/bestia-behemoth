package net.bestia.zone.auction.net

import net.bestia.bnet.proto.AnswerTradeAgreementCmsgProto
import net.bestia.zone.message.CMSG

data class AnswerTradeAgreementCMSG(
  override val playerId: Long,
  val offerId: Long,
  val accept: Boolean,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: AnswerTradeAgreementCmsgProto.AnswerTradeAgreementCMSG): AnswerTradeAgreementCMSG {
      return AnswerTradeAgreementCMSG(
        playerId = accountId,
        offerId = msg.offerId,
        accept = msg.accept,
      )
    }
  }
}
