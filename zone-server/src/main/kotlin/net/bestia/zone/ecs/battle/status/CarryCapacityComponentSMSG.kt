package net.bestia.zone.ecs.battle.status

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.CarryCapacityComponentSMSGProto
import net.bestia.zone.message.EntitySMSG

data class CarryCapacityComponentSMSG(
  override val entityId: Long,
  val current: Int,
  val max: Int
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val carryCapacityComponent = CarryCapacityComponentSMSGProto.CarryCapacityComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setCurrent(current)
      .setMax(max)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompCarryCapacity(carryCapacityComponent)
      .build()
  }
}
