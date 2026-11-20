package net.bestia.zone.stronghold

import net.bestia.bnet.proto.EmpyritePlantOfferSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG

/** Asks a member at a seed whether to plant the Empyrite there. Answered by clicking the sprout again with `plant`. */
data class EmpyritePlantOfferSMSG(
  val sproutEntityId: Long,
  val seedName: String,
  val coins: Long,
  val siteMinutes: Int,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val offer = EmpyritePlantOfferSmsgProto.EmpyritePlantOfferSMSG.newBuilder()
      .setSproutEntityId(sproutEntityId)
      .setSeedName(seedName)
      .setCoins(coins)
      .setSiteMinutes(siteMinutes)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setEmpyritePlantOffer(offer)
      .build()
  }
}
