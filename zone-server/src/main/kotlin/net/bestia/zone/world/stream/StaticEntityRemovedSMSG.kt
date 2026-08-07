package net.bestia.zone.world.stream

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.StaticEntityRemovedSMSGProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG

/**
 * One static entity is gone from a column its holders still have the ground for.
 *
 * The counterpart to [ChunkStaticEntitiesSMSG], and on the same channel for the same reasons. See the proto
 * for why a message this specific is needed at all when that one's note says removal needs no vanish: the
 * note covers the manifest withdrawing a chunk, not a prop that stops existing under a client still standing
 * on the ground it grew from.
 *
 * Sent to `ChunkSubscriptionService.subscribersOfColumn`, not to whoever is in interest range. Those sets are
 * not the same - a view volume reaches 176 m and the interest cube does not - and the set that matters is the
 * one that was *told about the prop*, which is definitionally the column's holders.
 */
data class StaticEntityRemovedSMSG(
  val chunk: ChunkPos,
  val entityId: Long
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val removed = StaticEntityRemovedSMSGProto.StaticEntityRemovedSMSG.newBuilder()
      .setPos(ChunkCoords.toProto(chunk))
      .setEntityId(entityId)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setStaticEntityRemoved(removed)
      .build()
  }
}
