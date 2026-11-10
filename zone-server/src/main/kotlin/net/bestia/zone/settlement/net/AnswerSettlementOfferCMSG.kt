package net.bestia.zone.settlement.net

import net.bestia.bnet.proto.AnswerSettlementOfferCmsgProto
import net.bestia.zone.message.CMSG

data class AnswerSettlementOfferCMSG(
  override val playerId: Long,
  val townId: Long,
  val accept: Boolean,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: AnswerSettlementOfferCmsgProto.AnswerSettlementOfferCMSG): AnswerSettlementOfferCMSG {
      return AnswerSettlementOfferCMSG(playerId = accountId, townId = msg.settlementId, accept = msg.accept)
    }
  }
}
