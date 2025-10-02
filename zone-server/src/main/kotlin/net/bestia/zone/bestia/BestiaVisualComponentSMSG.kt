package net.bestia.zone.bestia

import net.bestia.bnet.proto.BestiaVisualComponentProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG

data class BestiaVisualComponentSMSG(
  val entityId: Long,
  val bestiaId: Int
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val bestiaVisualComponent = BestiaVisualComponentProto.BestiaVisualComponent.newBuilder()
      .setEntityId(entityId)
      .setBestiaId(bestiaId)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompBestiaVisual(bestiaVisualComponent)
      .build()
  }
}