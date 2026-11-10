package net.bestia.zone.auction

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PickupsSmsgProto
import net.bestia.zone.message.SMSG
import java.time.Instant

/** The Won & Bought window: what waits for the receiver at linked houses. */
data class PickupsSMSG(val pickups: List<Row>) : SMSG {

  data class Row(
    val pickupId: Long,
    val itemId: Long,
    val amount: Int,
    val townName: String,
    val expiresAt: Instant,
    val postFee: Long,
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val msg = PickupsSmsgProto.PickupsSMSG.newBuilder()
      .addAllPickups(
        pickups.map {
          PickupsSmsgProto.Pickup.newBuilder()
            .setPickupId(it.pickupId)
            .setItemId(it.itemId)
            .setAmount(it.amount)
            .setTownName(it.townName)
            .setExpiresAtEpochSeconds(it.expiresAt.epochSecond)
            .setPostFee(it.postFee)
            .build()
        }
      )
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setPickups(msg)
      .build()
  }
}
