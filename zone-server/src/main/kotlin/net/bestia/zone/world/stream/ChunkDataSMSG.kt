package net.bestia.zone.world.stream

import com.google.protobuf.ByteString
import net.bestia.bnet.proto.ChunkDataSMSGProto
import net.bestia.bnet.proto.ChunkProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG

/**
 * A whole merged chunk.
 *
 * The payload arrives here already encoded and, if it helped, already compressed - see
 * [ChunkService.encodedOf]. This class does not compress, because whether to was decided when the blob was
 * built and cached, and doing it again per recipient is the cost the whole design exists to avoid.
 */
data class ChunkDataSMSG(
  val chunk: ChunkPos,
  val revision: Int,
  val encoding: Encoding,
  val compression: Compression,
  val payload: ByteArray,
  val baseHash: Long
) : SMSG {

  /** Mirrors `ChunkEncoding` in the proto. `RLE_V2` is what `RleCodec` writes. */
  enum class Encoding { RLE_V2 }

  /** Mirrors `ChunkCompression`. `NONE` is not a fallback - it wins outright on small chunks. */
  enum class Compression { NONE, DEFLATE }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val data = ChunkDataSMSGProto.ChunkDataSMSG.newBuilder()
      .setPos(ChunkCoords.toProto(chunk))
      .setRevision(revision)
      .setEncoding(
        when (encoding) {
          Encoding.RLE_V2 -> ChunkProto.ChunkEncoding.CHUNK_ENCODING_RLE_V2
        }
      )
      .setCompression(
        when (compression) {
          Compression.NONE -> ChunkProto.ChunkCompression.CHUNK_COMPRESSION_NONE
          Compression.DEFLATE -> ChunkProto.ChunkCompression.CHUNK_COMPRESSION_DEFLATE
        }
      )
      .setPayload(ByteString.copyFrom(payload))
      .setBaseHash(baseHash)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setChunkData(data)
      .build()
  }

  // A ByteArray field means the generated equals/hashCode compare references, which for a message that
  // tests round-trip is the difference between a passing assertion and a meaningless one.

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChunkDataSMSG) return false

    return chunk == other.chunk &&
        revision == other.revision &&
        encoding == other.encoding &&
        compression == other.compression &&
        baseHash == other.baseHash &&
        payload.contentEquals(other.payload)
  }

  override fun hashCode(): Int {
    var result = chunk.hashCode()
    result = 31 * result + revision
    result = 31 * result + encoding.hashCode()
    result = 31 * result + compression.hashCode()
    result = 31 * result + baseHash.hashCode()
    result = 31 * result + payload.contentHashCode()
    return result
  }

  /** Without this, a trace log of one chunk prints three kilobytes of escaped bytes. */
  override fun toString() =
    "ChunkDataSMSG[$chunk rev $revision, $encoding/$compression, ${payload.size} B]"
}
