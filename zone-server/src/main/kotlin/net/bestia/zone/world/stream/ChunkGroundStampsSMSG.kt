package net.bestia.zone.world.stream

import com.google.protobuf.ByteString
import net.bestia.bnet.proto.ChunkGroundStampsSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG

/**
 * What has passed over one chunk column, with a shape and a heading.
 *
 * Rides behind the chunk payload and is dropped with it, as [ChunkGroundLayersSMSG] is. See the proto for the
 * byte layout and for why stamps travel apart from the layers.
 *
 * @property stamps packed stamps, or null for a column whose tracks have all faded - which is how they retire
 */
data class ChunkGroundStampsSMSG(
  val chunk: ChunkPos,
  val stamps: ByteArray?
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val message = ChunkGroundStampsSMSGProto.ChunkGroundStampsSMSG.newBuilder()
      .setPos(ChunkCoords.toProto(chunk))
      .setEncoding(ChunkGroundStampsSMSGProto.ChunkGroundStampEncoding.CHUNK_GROUND_STAMP_ENCODING_PACKED_V1)

    stamps?.let { message.setStamps(ByteString.copyFrom(it)) }

    return EnvelopeProto.Envelope.newBuilder()
      .setChunkGroundStamps(message.build())
      .build()
  }

  /**
   * Deliberately terse, for [ChunkGroundLayersSMSG]'s reason: `net.bestia.zone` runs at TRACE in development
   * and protobuf's own `toString` escapes every byte. This message also belongs in `socket.filter-log-messages`.
   */
  override fun toString() = "ChunkGroundStampsSMSG($chunk, ${(stamps?.size ?: 0)}B)"

  // `ByteArray` gives data classes reference equality, which would make two identical payloads compare unequal.
  // See ChunkGroundOverlaySMSG: a message type whose equals lies is a trap for whoever trusts it.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChunkGroundStampsSMSG) return false

    return chunk == other.chunk && stamps.contentEquals(other.stamps)
  }

  override fun hashCode(): Int {
    return 31 * chunk.hashCode() + stamps.contentHashCode()
  }
}
