package net.bestia.zone.ecs.entity

import net.bestia.bnet.proto.AnimationComponentSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.EntitySMSG

data class AnimationSMSG(
  override val entityId: Long,
  val currentAnimation: Animation.AnimationKind,
) : EntitySMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val animationComp = AnimationComponentSMSGProto.AnimationComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setKind(currentAnimation.toProto())

    return EnvelopeProto.Envelope.newBuilder()
      .setCompAnimation(animationComp)
      .build()
  }

  companion object {
    private fun Animation.AnimationKind.toProto(): AnimationComponentSMSGProto.AnimationKind =
      when (this) {
        Animation.AnimationKind.IDLE -> AnimationComponentSMSGProto.AnimationKind.IDLE
        Animation.AnimationKind.WALK -> AnimationComponentSMSGProto.AnimationKind.WALK
      }
  }
}
