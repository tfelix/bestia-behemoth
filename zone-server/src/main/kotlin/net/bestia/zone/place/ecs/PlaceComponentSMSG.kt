package net.bestia.zone.place.ecs

import net.bestia.bnet.proto.PlaceComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class PlaceComponentSMSG(
  override val entityId: Long,
  val name: String,
  val settlement: SettlementArea? = null
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val component = PlaceComponentSMSGProto.PlaceComponentSMSG.newBuilder()
      .setName(name)
    settlement?.let {
      component.setSettlement(
        PlaceComponentSMSGProto.SettlementArea.newBuilder().setX(it.x).setY(it.y).setRadius(it.radius)
      )
    }

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setPlace(component))
  }
}
