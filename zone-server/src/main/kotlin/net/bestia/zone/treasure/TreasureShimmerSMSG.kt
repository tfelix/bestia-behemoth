package net.bestia.zone.treasure

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.TreasureShimmerSMSGProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.SMSG

/** Where Treasure Sense makes the ground shimmer, for the caster alone. */
data class TreasureShimmerSMSG(
  val spots: List<Vec3L>,
  val seconds: Float,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val shimmer = TreasureShimmerSMSGProto.TreasureShimmerSMSG.newBuilder().setSeconds(seconds)
    for (spot in spots) {
      shimmer.addSpots(Vec3OuterClass.Vec3.newBuilder().setX(spot.x).setY(spot.y).setZ(spot.z))
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setTreasureShimmer(shimmer)
      .build()
  }
}
