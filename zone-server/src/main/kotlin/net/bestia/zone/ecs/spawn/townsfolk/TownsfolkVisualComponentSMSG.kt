package net.bestia.zone.ecs.spawn.townsfolk

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.TownsfolkVisualComponentSMSGProto
import net.bestia.zone.message.EntitySMSG

data class TownsfolkVisualComponentSMSG(
  override val entityId: Long,
  val name: String,
  val body: TownsfolkBody
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val component = TownsfolkVisualComponentSMSGProto.TownsfolkVisualComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setName(name)
      .setBody(body.toBnet())
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompTownsfolkVisual(component)
      .build()
  }
}
