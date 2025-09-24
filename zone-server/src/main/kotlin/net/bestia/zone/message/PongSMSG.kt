package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto.Envelope
import net.bestia.bnet.proto.PingOuterClass

object PongSMSG : SMSG {
  override fun toBnetEnvelope(): Envelope {
    val pong = PingOuterClass.Pong.newBuilder().build()
    return Envelope.newBuilder().setPong(pong).build()
  }
}