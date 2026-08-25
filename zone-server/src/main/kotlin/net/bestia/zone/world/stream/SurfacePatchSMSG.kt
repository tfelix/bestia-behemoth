package net.bestia.zone.world.stream

import com.google.protobuf.ByteString
import net.bestia.bnet.proto.ChunkProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SurfacePatchProto
import net.bestia.bnet.proto.SurfacePatchSMSGProto
import net.bestia.worldgen.lod.PatchPos
import net.bestia.zone.message.SMSG

/**
 * One coarse patch of ground: the visible surface, without the voxels under it.
 *
 * Around two kilobytes for what sixty-four chunks would cost nearly two hundred, which is the trade the far
 * ring is made of.
 */
data class SurfacePatchSMSG(
  val pos: PatchPos,
  val compressed: Boolean,
  val payload: ByteArray
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val patch = SurfacePatchSMSGProto.SurfacePatchSMSG.newBuilder()
      .setPos(SurfacePatchCoords.toProto(pos))
      .setEncoding(SurfacePatchProto.SurfacePatchEncoding.SURFACE_PATCH_ENCODING_PLANES_V1)
      .setCompression(
        if (compressed) ChunkProto.ChunkCompression.CHUNK_COMPRESSION_DEFLATE
        else ChunkProto.ChunkCompression.CHUNK_COMPRESSION_NONE
      )
      .setPayload(ByteString.copyFrom(payload))

    return EnvelopeProto.Envelope.newBuilder()
      .setSurfacePatch(patch.build())
      .build()
  }

  // Hand-written because the payload is an array: the generated equals would compare it by identity, so two
  // messages carrying the same bytes would not be equal and a test asserting on one would silently never pass.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SurfacePatchSMSG) return false

    return pos == other.pos && compressed == other.compressed && payload.contentEquals(other.payload)
  }

  override fun hashCode(): Int {
    var result = pos.hashCode()
    result = 31 * result + compressed.hashCode()
    result = 31 * result + payload.contentHashCode()
    return result
  }
}
