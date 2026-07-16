package net.bestia.zone.ecs.battle.exp

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ExpComponentSMSGProto
import net.bestia.zone.message.EntitySMSG

data class ExpComponentSMSG(
  override val entityId: Long,
  val exp: Int,
  val requiredExpNextLevel: Int
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val expComponent = ExpComponentSMSGProto.ExpComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setExp(exp)
      .setRequiredExpNextLevel(requiredExpNextLevel)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompExp(expComponent)
      .build()
  }
}