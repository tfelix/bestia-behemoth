package net.bestia.zone.message.entity

import net.bestia.bnet.proto.*
import net.bestia.zone.ecs.visual.Animation as AnimationComponent

data class AnimationSMSG(
  override val entityId: Long,
  val sprite: String,
) : EntitySMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    TODO()
    /*
    val entity = EntityOuterClass.Entity.newBuilder()
      .setId(entityId)
      .build()

    val sprite = SpriteComponentOuterClass.SpriteComponent.newBuilder()
      .setSprite(sprite)
      .setEntity(entity)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompSprite(sprite)
      .build()*/
  }

  companion object {
    fun fromComponent(entityId: Long, comp: AnimationComponent): AnimationSMSG {
      return AnimationSMSG(
        entityId = entityId,
        sprite = comp.currentAnimation
      )
    }
  }
}