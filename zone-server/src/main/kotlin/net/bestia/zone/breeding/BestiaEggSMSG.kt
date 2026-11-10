package net.bestia.zone.breeding

import net.bestia.bnet.proto.BestiaEggProto
import net.bestia.bnet.proto.BestiaEggSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG

data class BestiaEggSMSG(
  val egg: BestiaEggProto.BestiaEgg,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setBestiaEgg(BestiaEggSmsgProto.BestiaEggSMSG.newBuilder().setEgg(egg))
      .build()
  }
}
