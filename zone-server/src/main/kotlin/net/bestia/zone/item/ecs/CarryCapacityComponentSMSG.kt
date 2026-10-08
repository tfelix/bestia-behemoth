package net.bestia.zone.item.ecs

import net.bestia.bnet.proto.CarryCapacityComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class CarryCapacityComponentSMSG(
  override val entityId: Long,
  val current: Int,
  val max: Int
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val carryCapacityComponent = CarryCapacityComponentSMSGProto.CarryCapacityComponentSMSG.newBuilder()
      .setCurrent(current)
      .setMax(max)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setCarryCapacity(carryCapacityComponent))
  }
}
