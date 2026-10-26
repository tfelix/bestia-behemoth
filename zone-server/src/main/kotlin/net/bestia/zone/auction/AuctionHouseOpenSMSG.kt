package net.bestia.zone.auction

import net.bestia.bnet.proto.AuctionHouseOpenSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class AuctionHouseOpenSMSG(
  val houseEntityId: EntityId,
  val townName: String,
  val linkedTownNames: List<String>,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val open = AuctionHouseOpenSmsgProto.AuctionHouseOpenSMSG.newBuilder()
      .setHouseEntityId(houseEntityId)
      .setTownName(townName)
      .addAllLinkedTownNames(linkedTownNames)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setAuctionHouseOpen(open)
      .build()
  }
}
