package net.bestia.zone.world.stream

import net.bestia.bnet.proto.ChunkStaticEntitiesSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG
import net.bestia.zone.world.prop.StaticEntityKind

/**
 * Every static entity standing in one chunk column.
 *
 * Rides behind the chunk payload, so what a client is told about is exactly the ground it holds. See the
 * proto for the measurement that made this a batch rather than a stream of ordinary entity messages: the
 * bytes were affordable either way and the *flush count* was not.
 */
data class ChunkStaticEntitiesSMSG(
  val chunk: ChunkPos,
  val entries: List<Entry>
) : SMSG {

  /**
   * @property z global voxel z, unlike [localX]/[localY] which are inside the chunk - a column spans the
   *   whole vertical extent, so a slab-local z would need the slab index to mean anything
   * @property halfLengthDm footprint half-extent along the facing, or 0 for a kind that takes its size from
   *   `prop-kinds.yml`. Nonzero only for a building, whose size is decided by the lot it stands on.
   * @property halfWidthDm the same across the facing; travels with [halfLengthDm] or not at all
   */
  data class Entry(
    val entityId: Long,
    val kind: StaticEntityKind,
    val variant: Int,
    val localX: Int,
    val localY: Int,
    val z: Int,
    val heightDm: Int,
    val yawCentiradians: Int,
    val halfLengthDm: Int = 0,
    val halfWidthDm: Int = 0
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val batch = ChunkStaticEntitiesSMSGProto.ChunkStaticEntitiesSMSG.newBuilder()
      .setPos(ChunkCoords.toProto(chunk))

    entries.forEach { entry ->
      batch.addEntries(
        ChunkStaticEntitiesSMSGProto.ChunkStaticEntitiesSMSG.Entry.newBuilder()
          .setEntityId(entry.entityId)
          .setKind(entry.kind.ordinal)
          .setVariant(entry.variant)
          .setLocalX(entry.localX)
          .setLocalY(entry.localY)
          .setZ(entry.z)
          .setHeightDm(entry.heightDm)
          .setYawCentiradians(entry.yawCentiradians)
          .setHalfLengthDm(entry.halfLengthDm)
          .setHalfWidthDm(entry.halfWidthDm)
          .build()
      )
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setChunkStaticEntities(batch.build())
      .build()
  }

  /**
   * Deliberately terse, and for the same reason `ChunkDataSMSG`'s is.
   *
   * `ChannelRegistry.sendMessage` stringifies every envelope when trace is on and `net.bestia.zone` runs at
   * TRACE in development, so a default `toString` over a few hundred entries would put tens of kilobytes of
   * log on the path of every chunk. This message also belongs in `socket.filter-log-messages`.
   */
  override fun toString() = "ChunkStaticEntitiesSMSG($chunk, ${entries.size} entries)"
}
