package net.bestia.zone.capture

import net.bestia.bnet.proto.BestiaCaptureSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class BestiaCaptureSMSG(
  val trapEntityId: EntityId,
  val targetEntityId: EntityId,
  val trapperEntityId: EntityId,
  val success: Boolean,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val capture = BestiaCaptureSmsgProto.BestiaCaptureSMSG.newBuilder()
      .setTrapEntityId(trapEntityId)
      .setTargetEntityId(targetEntityId)
      .setTrapperEntityId(trapperEntityId)
      .setSuccess(success)

    return EnvelopeProto.Envelope.newBuilder()
      .setBestiaCapture(capture)
      .build()
  }
}
