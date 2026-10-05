package net.bestia.zone.townsfolk.ecs

import net.bestia.bnet.proto.TownsfolkVisualComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class TownsfolkVisualComponentSMSG(
  override val entityId: Long,
  val name: String,
  val body: TownsfolkBody
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val component = TownsfolkVisualComponentSMSGProto.TownsfolkVisualComponentSMSG.newBuilder()
      .setName(name)
      .setBody(body.toBnet())
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setTownsfolkVisual(component))
  }
}
