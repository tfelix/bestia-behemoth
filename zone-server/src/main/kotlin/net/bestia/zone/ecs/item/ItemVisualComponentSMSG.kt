package net.bestia.zone.ecs.item

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ItemVisualComponentProto
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

class ItemVisualComponentSMSG(
  override val entityId: EntityId,
  val itemId: Int,
  val amount: Int,
  val uniqueId: Long
) : EntitySMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val comp = ItemVisualComponentProto.ItemVisualComponent
      .newBuilder()
      .setEntityId(entityId)
      .setItemId(itemId)
      .setAmount(amount)
      .setUniqueId(uniqueId)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompItemVisual(comp)
      .build()
  }
}