package net.bestia.zone.auction

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.TradeAgreementOfferSmsgProto
import net.bestia.zone.message.SMSG

data class TradeAgreementOfferSMSG(val offerId: Long, val fromName: String) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val msg = TradeAgreementOfferSmsgProto.TradeAgreementOfferSMSG.newBuilder()
      .setOfferId(offerId)
      .setFromName(fromName)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setTradeAgreementOffer(msg)
      .build()
  }
}
