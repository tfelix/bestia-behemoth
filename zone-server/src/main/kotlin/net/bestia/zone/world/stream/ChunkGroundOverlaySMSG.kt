package net.bestia.zone.world.stream

import com.google.protobuf.ByteString
import net.bestia.bnet.proto.ChunkGroundOverlaySMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG

/**
 * Which of one chunk column's square metres are burnt, and which are alight.
 *
 * Rides behind the chunk payload and is dropped with it, exactly as [ChunkStaticEntitiesSMSG] is. See the
 * proto for why scorch travels beside the ground rather than in it, and why both masks share one message.
 *
 * @property scorched `chunkSize²` bits, or null when none of this column is burnt
 * @property burning the same, or null when nothing here is alight - which is almost always
 */
data class ChunkGroundOverlaySMSG(
  val chunk: ChunkPos,
  val scorched: ByteArray?,
  val burning: ByteArray?
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val overlay = ChunkGroundOverlaySMSGProto.ChunkGroundOverlaySMSG.newBuilder()
      .setPos(ChunkCoords.toProto(chunk))
      .setEncoding(
        ChunkGroundOverlaySMSGProto.ChunkGroundOverlayEncoding.CHUNK_GROUND_OVERLAY_ENCODING_BITMASK_V1
      )

    // Left unset rather than set empty, so proto3's own omission is what says "nothing here" and the message
    // for a clean column stays about twelve bytes.
    scorched?.let { overlay.scorched = ByteString.copyFrom(it) }
    burning?.let { overlay.burning = ByteString.copyFrom(it) }

    return EnvelopeProto.Envelope.newBuilder()
      .setChunkGroundOverlay(overlay.build())
      .build()
  }

  /**
   * Deliberately terse, for [ChunkStaticEntitiesSMSG]'s reason and more sharply.
   *
   * `net.bestia.zone` runs at TRACE in development and protobuf's own `toString` escapes every byte, so 256
   * bytes of mask stringifies to well over a kilobyte - several times a second, per column, for the whole
   * length of a fire. This message also belongs in `socket.filter-log-messages`.
   */
  override fun toString() =
    "ChunkGroundOverlaySMSG($chunk, ${scorched?.size ?: 0}B scorched, ${burning?.size ?: 0}B burning)"

  // `ByteArray` gives data classes reference equality, which would make two identical overlays compare
  // unequal - and this message is deduplicated by value nowhere today, but a message type whose equals lies
  // is a trap laid for whoever tries.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChunkGroundOverlaySMSG) return false
    return chunk == other.chunk &&
        scorched.contentEquals(other.scorched) &&
        burning.contentEquals(other.burning)
  }

  override fun hashCode(): Int {
    var result = chunk.hashCode()
    result = 31 * result + scorched.contentHashCode()
    result = 31 * result + burning.contentHashCode()
    return result
  }
}
