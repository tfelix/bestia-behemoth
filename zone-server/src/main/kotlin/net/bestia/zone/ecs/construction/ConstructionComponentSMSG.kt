package net.bestia.zone.ecs.construction

import net.bestia.bnet.proto.ConstructionComponentSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.EntitySMSG

/**
 * In-range broadcast of a construction site's progress, driving how far the finished art has faded in.
 * Produced by [ConstructionSite.toEntityMessage].
 *
 * Not [net.bestia.zone.ecs.battle.skill.CastingComponentSMSG], which a craft does reuse - see the proto.
 */
data class ConstructionComponentSMSG(
  override val entityId: Long,
  val remainingSeconds: Float,
  val totalSeconds: Float,
  val active: Boolean,
  val removed: Boolean = false
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = ConstructionComponentSmsgProto.ConstructionComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setRemainingSeconds(remainingSeconds)
      .setTotalSeconds(totalSeconds)
      .setActive(active)
      .setRemoved(removed)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompConstruction(proto)
      .build()
  }
}
