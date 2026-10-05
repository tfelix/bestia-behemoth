package net.bestia.zone.place.ecs

import net.bestia.bnet.proto.AreaNameComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class AreaNameComponentSMSG(
  override val entityId: Long,
  val name: String,
  val radius: Long
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val component = AreaNameComponentSMSGProto.AreaNameComponentSMSG.newBuilder()
      .setName(name)
      .setRadius(radius)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setAreaName(component))
  }
}
