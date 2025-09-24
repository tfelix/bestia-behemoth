package net.bestia.zone.ecs.visual

import net.bestia.bnet.proto.BestiaVisualComponentProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

class BestiaVisualComponentSMSG(
  val entityId: EntityId,
  val bestiaId: Int
) : SMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val comp = BestiaVisualComponentProto.BestiaVisualComponent
      .newBuilder()
      .setBestiaId(bestiaId)
      .setEntityId(entityId)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompBestiaVisual(comp)
      .build()
  }
}