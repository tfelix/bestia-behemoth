package net.bestia.zone.settlement

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SettlementOfferSmsgProto
import net.bestia.zone.message.SMSG

data class SettlementOfferSMSG(val townId: Long, val name: String, val fromName: String) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val offer = SettlementOfferSmsgProto.SettlementOfferSMSG.newBuilder()
      .setSettlementId(townId)
      .setName(name)
      .setFromName(fromName)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setSettlementOffer(offer)
      .build()
  }
}
