package net.bestia.zone.ecs.entity

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.VisualComponentProto
import net.bestia.zone.message.EntitySMSG

data class VisualComponentSMSG(
  override val entityId: Long,
  val kind: VisualKind,
  val visualId: Long
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val visual = VisualComponentProto.VisualComponent.newBuilder()
      .setEntityId(entityId)
      .setKind(kind.toBnet())
      .setId(visualId)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompVisual(visual)
      .build()
  }
}
