package net.bestia.zone.auction

import net.bestia.bnet.proto.AuctionPurchasedSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG

data class AuctionPurchasedSMSG(
  val listingId: Long,
  val itemId: Long,
  val amount: Int,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val purchased = AuctionPurchasedSmsgProto.AuctionPurchasedSMSG.newBuilder()
      .setListingId(listingId)
      .setItemId(itemId)
      .setAmount(amount)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setAuctionPurchased(purchased)
      .build()
  }
}
