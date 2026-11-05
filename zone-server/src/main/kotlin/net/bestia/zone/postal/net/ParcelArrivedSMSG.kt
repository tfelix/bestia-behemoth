package net.bestia.zone.postal.net

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ParcelArrivedSmsgProto
import net.bestia.zone.message.SMSG

data class ParcelArrivedSMSG(
  val senderName: String,
  val officeName: String,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setParcelArrived(
        ParcelArrivedSmsgProto.ParcelArrivedSMSG.newBuilder().setSenderName(senderName).setOfficeName(officeName)
      )
      .build()
  }
}
