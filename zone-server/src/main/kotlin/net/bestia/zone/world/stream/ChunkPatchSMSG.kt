package net.bestia.zone.world.stream

import com.google.protobuf.ByteString
import net.bestia.bnet.proto.ChunkPatchSMSGProto
import net.bestia.bnet.proto.ChunkProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG

/**
 * The voxels a player removed from one chunk. See the proto for the arithmetic that justifies it existing.
 */
data class ChunkPatchSMSG(
  val chunk: ChunkPos,
  val fromRevision: Int,
  val toRevision: Int,
  val removals: ByteArray,
  /**
   * How many voxels this describes.
   *
   * A stored field rather than `removals.size / bytesPerRemoval`. That division was correct only while every
   * edit was a fixed five bytes; with delta-coded varint indices a removal is one to four bytes, so dividing
   * would return a number that merely looks plausible - and it is read by the logging and by the
   * patch-versus-snapshot decision.
   */
  val removalCount: Int
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val patch = ChunkPatchSMSGProto.ChunkPatchSMSG.newBuilder()
      .setPos(ChunkCoords.toProto(chunk))
      .setFromRevision(fromRevision)
      .setToRevision(toRevision)
      .setRemovals(ByteString.copyFrom(removals))
      .setEncoding(ChunkProto.ChunkPatchEncoding.CHUNK_PATCH_ENCODING_REMOVAL_V1)
      .setRemovalCount(removalCount)
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
        removalCount == other.removalCount &&
        removals.contentEquals(other.removals)
  }

  override fun hashCode(): Int {
    var result = chunk.hashCode()
    result = 31 * result + fromRevision
    result = 31 * result + toRevision
    result = 31 * result + removalCount
    result = 31 * result + removals.contentHashCode()
    return result
  }

  override fun toString() =
    "ChunkPatchSMSG[$chunk rev $fromRevision->$toRevision, $removalCount removals, ${removals.size} B]"

  companion object {

    /**
     * @param removals packed `(voxelIndex shl 8) or remainingOccupancy`, sorted ascending
     */
    fun of(chunk: ChunkPos, fromRevision: Int, toRevision: Int, removals: IntArray) = ChunkPatchSMSG(
      chunk = chunk,
      fromRevision = fromRevision,
      toRevision = toRevision,
      removals = ChunkPatchCodec.encode(removals),
      removalCount = removals.size
    )
  }
}
