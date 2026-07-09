package net.bestia.zone.ecs.bestia

import net.bestia.bnet.proto.BestiaVisualComponentProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.EntitySMSG

data class BestiaVisualComponentSMSG(
  override val entityId: Long,
  val bestiaId: Long
) : EntitySMSG {

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
