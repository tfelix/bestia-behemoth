package net.bestia.zone.auction

import net.bestia.bnet.proto.AuctionPurchasedSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG

data class AuctionPurchasedSMSG(
  val listingId: Long,
  val itemId: Long,
  val amount: Int,
  /** The town of the linked house the goods wait at; empty when they were handed over. */
  val waitingAt: String = "",
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val purchased = AuctionPurchasedSmsgProto.AuctionPurchasedSMSG.newBuilder()
      .setListingId(listingId)
      .setItemId(itemId)
      .setAmount(amount)
      .setWaitingAt(waitingAt)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setAuctionPurchased(purchased)
      .build()
  }
}
