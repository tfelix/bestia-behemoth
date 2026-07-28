package net.bestia.zone.world.stream

import net.bestia.bnet.proto.ChunkManifestSMSGProto
import net.bestia.bnet.proto.ChunkProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG

/**
 * What this client is entitled to, as positions and the revision each is at.
 *
 * Roughly a byte and a half per chunk against three kilobytes of payload, so announcing is two orders of
 * magnitude cheaper than sending - and a client that already holds a listed revision asks for nothing.
 * It is also the authorisation set: [ChunkRequestHandler] serves only what a manifest has offered.
 */
data class ChunkManifestSMSG(
  /** Replace the client's set rather than amend it. Then [removed] is empty and [added] is everything. */
  val reset: Boolean,
  val added: List<Ref>,
  val removed: List<ChunkPos>
) : SMSG {

  data class Ref(val chunk: ChunkPos, val revision: Int)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val manifest = ChunkManifestSMSGProto.ChunkManifestSMSG.newBuilder()
      .setReset(reset)

    added.forEach { ref ->
      manifest.addAdded(
        ChunkProto.ChunkRef.newBuilder()
          .setPos(ChunkCoords.toProto(ref.chunk))
          .setRevision(ref.revision)
      )
    }

    removed.forEach { manifest.addRemoved(ChunkCoords.toProto(it)) }

    return EnvelopeProto.Envelope.newBuilder()
      .setChunkManifest(manifest.build())
      .build()
  }
}
