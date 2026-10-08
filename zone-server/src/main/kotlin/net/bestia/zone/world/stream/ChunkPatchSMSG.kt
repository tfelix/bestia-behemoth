package net.bestia.zone.world.stream

import com.google.protobuf.ByteString
import net.bestia.bnet.proto.ChunkPatchSMSGProto
import net.bestia.bnet.proto.ChunkProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG

/**
 * The voxels that changed in one chunk. See the proto for the arithmetic that justifies it existing.
 */
data class ChunkPatchSMSG(
  val chunk: ChunkPos,
  val fromRevision: Int,
  val toRevision: Int,
  val edits: ByteArray,
  /** Carried rather than divided out of [edits]: varint index gaps make an edit's size vary. */
  val editCount: Int
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val patch = ChunkPatchSMSGProto.ChunkPatchSMSG.newBuilder()
      .setPos(ChunkCoords.toProto(chunk))
      .setFromRevision(fromRevision)
      .setToRevision(toRevision)
      .setEdits(ByteString.copyFrom(edits))
      .setEncoding(ChunkProto.ChunkPatchEncoding.CHUNK_PATCH_ENCODING_EDIT_V1)
      .setEditCount(editCount)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setChunkPatch(patch)
      .build()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChunkPatchSMSG) return false

    return chunk == other.chunk &&
        fromRevision == other.fromRevision &&
        toRevision == other.toRevision &&
        editCount == other.editCount &&
        edits.contentEquals(other.edits)
  }

  override fun hashCode(): Int {
    var result = chunk.hashCode()
    result = 31 * result + fromRevision
    result = 31 * result + toRevision
    result = 31 * result + editCount
    result = 31 * result + edits.contentHashCode()
    return result
  }

  override fun toString(): String {
    return "ChunkPatchSMSG[$chunk rev $fromRevision->$toRevision, $editCount edits, ${edits.size} B]"
  }

  companion object {

    /** @param edits [net.bestia.worldgen.derived.VoxelEdit]s, sorted ascending */
    fun of(chunk: ChunkPos, fromRevision: Int, toRevision: Int, edits: LongArray): ChunkPatchSMSG {
      return ChunkPatchSMSG(
        chunk = chunk,
        fromRevision = fromRevision,
        toRevision = toRevision,
        edits = ChunkEditCodec.encode(edits),
        editCount = edits.size
      )
    }
  }
}
