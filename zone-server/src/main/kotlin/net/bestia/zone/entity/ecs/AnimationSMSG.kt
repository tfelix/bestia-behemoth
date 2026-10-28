package net.bestia.zone.entity.ecs

import net.bestia.bnet.proto.AnimationComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class AnimationSMSG(
  override val entityId: Long,
  val currentAnimation: Animation.AnimationKind,
) : EntitySMSG {
  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val animationComp = AnimationComponentSMSGProto.AnimationComponentSMSG.newBuilder()
      .setKind(currentAnimation.toProto())

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setAnimation(animationComp))
  }

  companion object {
    fun Animation.AnimationKind.toProto(): AnimationComponentSMSGProto.AnimationKind =
      when (this) {
        Animation.AnimationKind.IDLE -> AnimationComponentSMSGProto.AnimationKind.IDLE
        Animation.AnimationKind.SLEEP -> AnimationComponentSMSGProto.AnimationKind.SLEEP
        Animation.AnimationKind.CHEER -> AnimationComponentSMSGProto.AnimationKind.CHEER
        Animation.AnimationKind.JUMP -> AnimationComponentSMSGProto.AnimationKind.JUMP
        Animation.AnimationKind.POINT -> AnimationComponentSMSGProto.AnimationKind.POINT
        Animation.AnimationKind.FLOURISH -> AnimationComponentSMSGProto.AnimationKind.FLOURISH
        Animation.AnimationKind.SIT -> AnimationComponentSMSGProto.AnimationKind.SIT
        Animation.AnimationKind.LIE -> AnimationComponentSMSGProto.AnimationKind.LIE
      }

    /** Null for a value this server does not know, which only a client built against a newer schema sends. */
    fun AnimationComponentSMSGProto.AnimationKind.fromProto(): Animation.AnimationKind? =
      when (this) {
        AnimationComponentSMSGProto.AnimationKind.IDLE -> Animation.AnimationKind.IDLE
        AnimationComponentSMSGProto.AnimationKind.SLEEP -> Animation.AnimationKind.SLEEP
        AnimationComponentSMSGProto.AnimationKind.CHEER -> Animation.AnimationKind.CHEER
        AnimationComponentSMSGProto.AnimationKind.JUMP -> Animation.AnimationKind.JUMP
        AnimationComponentSMSGProto.AnimationKind.POINT -> Animation.AnimationKind.POINT
        AnimationComponentSMSGProto.AnimationKind.FLOURISH -> Animation.AnimationKind.FLOURISH
        AnimationComponentSMSGProto.AnimationKind.SIT -> Animation.AnimationKind.SIT
        AnimationComponentSMSGProto.AnimationKind.LIE -> Animation.AnimationKind.LIE
        AnimationComponentSMSGProto.AnimationKind.UNRECOGNIZED -> null
      }
  }
}
