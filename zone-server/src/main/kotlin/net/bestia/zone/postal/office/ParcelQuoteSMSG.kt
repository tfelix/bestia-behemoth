package net.bestia.zone.postal.office

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ParcelQuoteSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.postal.parcel.ParcelService

data class ParcelQuoteSMSG(
  val quote: ParcelService.Quote,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setParcelQuote(
        ParcelQuoteSmsgProto.ParcelQuoteSMSG.newBuilder()
          .setRecipientName(quote.recipientName)
          .setDestinationName(quote.destinationName)
          .setWeightUnits(quote.weightUnits)
          .setFee(quote.fee)
          .setLatestArrivalEpochSeconds(quote.latestArrival.epochSecond)
      )
      .build()
  }
}
