package net.bestia.zone.ecs.battle.skill

import net.bestia.bnet.proto.CastingComponentSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.EntitySMSG

/**
 * In-range broadcast of a running cast, driving the cast bar above the entity's head. Produced by
 * [Casting.toEntityMessage]; re-sent while the cast runs so the client stays corrected.
 */
data class CastingComponentSMSG(
  override val entityId: Long,
  val remainingSeconds: Float,
  val totalSeconds: Float,
  val removed: Boolean = false
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = CastingComponentSmsgProto.CastingComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setRemainingSeconds(remainingSeconds)
      .setTotalSeconds(totalSeconds)
      .setRemoved(removed)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompCasting(proto)
      .build()
  }
}
