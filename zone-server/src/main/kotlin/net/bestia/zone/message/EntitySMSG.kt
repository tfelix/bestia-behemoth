package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.StateBatchSmsgProto

/** Entity state for the client. It always travels inside a [StateBatchSMSG], which names the entity once. */
interface EntitySMSG : SMSG {
  val entityId: Long

  /** Adds this message to the update of [entityId]. */
  fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder)

  /** Sent on its own, it is a batch of one that belongs to no tick. */
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return StateBatchSMSG.outsideTick(listOf(EntityUpdate(entityId, listOf(this)))).toBnetEnvelope()
  }
}
