package net.bestia.zone.world.stream

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SurfacePatchManifestSMSGProto
import net.bestia.worldgen.lod.PatchPos
import net.bestia.zone.message.SMSG

/**
 * Which coarse patches this client is entitled to.
 *
 * The same announce-then-pull contract [ChunkManifestSMSG] has, minus revisions: a patch is a pure function
 * of the heightfield, so one a client holds is one it never has to be told about again.
 */
data class SurfacePatchManifestSMSG(
  /** Replace the client's set rather than amend it. Then [removed] is empty and [added] is everything. */
  val reset: Boolean,
  val added: List<PatchPos>,
  val removed: List<PatchPos>
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val manifest = SurfacePatchManifestSMSGProto.SurfacePatchManifestSMSG.newBuilder()
      .setReset(reset)

    added.forEach { manifest.addAdded(SurfacePatchCoords.toProto(it)) }
    removed.forEach { manifest.addRemoved(SurfacePatchCoords.toProto(it)) }

    return EnvelopeProto.Envelope.newBuilder()
      .setSurfacePatchManifest(manifest.build())
      .build()
  }
}
