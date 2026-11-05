package net.bestia.zone.postal.office

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ParcelSentSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.postal.parcel.ParcelService

data class ParcelSentSMSG(
  val quote: ParcelService.Quote,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setParcelSent(
        ParcelSentSmsgProto.ParcelSentSMSG.newBuilder()
          .setRecipientName(quote.recipientName)
          .setDestinationName(quote.destinationName)
          .setLatestArrivalEpochSeconds(quote.latestArrival.epochSecond)
      )
      .build()
  }
}
