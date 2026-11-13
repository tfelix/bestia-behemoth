package net.bestia.zone.scry

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ScrySightSmsgProto
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.SMSG

/** Tells the caster where its Scry looks, or that it ended. [centre] is null once it ended. */
data class ScrySightSMSG(
  val centre: Vec3L?,
  val radiusMetres: Double = 0.0,
  val seconds: Double = 0.0,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = ScrySightSmsgProto.ScrySightSMSG.newBuilder().setOpen(centre != null)
    if (centre != null) {
      proto.setX(centre.x).setY(centre.y).setZ(centre.z)
        .setRadiusMetres(radiusMetres.toFloat())
        .setSeconds(seconds.toFloat())
    }

    return EnvelopeProto.Envelope.newBuilder().setScrySight(proto).build()
  }

  companion object {
    val ENDED = ScrySightSMSG(centre = null)
  }
}
