package net.bestia.zone.component

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ManaComponentSMSGProto
import net.bestia.zone.message.SMSG

data class ManaComponentSMSG(
  val entityId: Long,
  val current: Int,
  val max: Int
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val manaComponent = ManaComponentSMSGProto.ManaComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setCurrent(current)
      .setMax(max)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompMana(manaComponent)
      .build()
  }
}
