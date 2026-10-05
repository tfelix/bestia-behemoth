package net.bestia.zone.entity.ecs

import net.bestia.bnet.proto.VisualComponentProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class VisualComponentSMSG(
  override val entityId: Long,
  val kind: VisualKind,
  val visualId: Long
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val visual = VisualComponentProto.VisualComponent.newBuilder()
      .setKind(kind.toBnet())
      .setId(visualId)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setVisual(visual))
  }
}
