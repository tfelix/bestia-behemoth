package net.bestia.zone.system

import net.bestia.bnet.proto.EnvelopeProto.Envelope
import net.bestia.bnet.proto.PingOuterClass
import net.bestia.zone.message.SMSG

object PongSMSG : SMSG {
  override fun toBnetEnvelope(): Envelope {
    val pong = PingOuterClass.Pong.newBuilder().build()
    return Envelope.newBuilder().setPong(pong).build()
  }
}