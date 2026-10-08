package net.bestia.zone.place.ecs

import net.bestia.bnet.proto.PlaceComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class PlaceComponentSMSG(
  override val entityId: Long,
  val name: String
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val component = PlaceComponentSMSGProto.PlaceComponentSMSG.newBuilder()
      .setName(name)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setPlace(component))
  }
}
