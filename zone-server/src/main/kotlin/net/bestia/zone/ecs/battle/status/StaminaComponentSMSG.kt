package net.bestia.zone.ecs.battle.status

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.StaminaComponentSMSGProto
import net.bestia.zone.message.EntitySMSG

data class StaminaComponentSMSG(
  override val entityId: Long,
  val current: Int,
  val max: Int
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val staminaComponent = StaminaComponentSMSGProto.StaminaComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setCurrent(current)
      .setMax(max)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompStamina(staminaComponent)
      .build()
  }
}
