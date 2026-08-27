package net.bestia.zone.ecs.battle.damage

import net.bestia.bnet.proto.DeadComponentSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.EntitySMSG

/**
 * Tells everyone in range that a player-owned entity is lying dead, and - with [removed] - that it is
 * back on its feet. Produced by [Dead.toEntityMessage].
 */
data class DeadComponentSMSG(
  override val entityId: Long,
  val removed: Boolean = false
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = DeadComponentSmsgProto.DeadComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setRemoved(removed)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompDead(proto)
      .build()
  }
}
