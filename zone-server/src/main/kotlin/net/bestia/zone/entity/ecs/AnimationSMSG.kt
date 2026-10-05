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
    private fun Animation.AnimationKind.toProto(): AnimationComponentSMSGProto.AnimationKind =
      when (this) {
        Animation.AnimationKind.IDLE -> AnimationComponentSMSGProto.AnimationKind.IDLE
        Animation.AnimationKind.SLEEP -> AnimationComponentSMSGProto.AnimationKind.SLEEP
      }
  }
}
