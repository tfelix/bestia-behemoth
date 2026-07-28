package net.bestia.worldgen.store

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.ChunkEngine
import net.bestia.worldgen.voxel.RleCodec

/**
 * Everything that has to match between a client and a server for the client to be allowed to generate its
 * own base chunks.
 *
 * Three components, and each of them can break generation independently:
 *
 * - [pipelineVersion] - the stage version vector. Different terrain.
 * - [blockPaletteVersion] - a hash of the block id assignment. Same terrain, different rock.
 * - [chunkFormatVersion] - the RLE format. Cannot even be decoded.
 *
 * A single opaque number would be simpler to compare and useless to diagnose; a mismatch would say only
 * "incompatible", and the difference between "your client is one patch behind" and "your client cannot read
 * this format" is the difference between a useful message and a support ticket.
 *
 * **Server-side only.** This is a cache key and a boot gate; what goes over the wire is the single
 * [ChunkEngine.VERSION], because a client that receives merged chunks cannot act on the distinction.
 */
data class PipelineVersion(
  val pipelineVersion: Long,
  val blockPaletteVersion: Long,
  val chunkFormatVersion: Int
) {

  override fun toString() =
    "pipeline=${pipelineVersion.toString(16)} palette=${blockPaletteVersion.toString(16)} " +
        "format=$chunkFormatVersion"

  companion object {

    /** The version this build speaks. */
    fun current(pipelineVersion: Long) = PipelineVersion(
      pipelineVersion = pipelineVersion,
      blockPaletteVersion = paletteVersion(),
      chunkFormatVersion = RleCodec.VERSION
    )

    /**
     * A hash over the block palette's *name to id* mapping.
     *
     * Over names and ids rather than over the enum's declaration order, because the ids are the thing that
     * ends up in stored data. Reordering the enum must not invalidate anything; renumbering an id must
     * invalidate everything.
     */
    fun paletteVersion(): Long {
      var h = 0L
      for (block in BlockType.entries.sortedBy { it.id }) {
        h = GenRng.hash(h, block.id.toLong(), GenRng.hashString(block.name))
      }
      return h
    }
  }
}

/**
 * The hard version gate at login.
 *
 * Deliberately all-or-nothing. Partial compatibility between a client and a server that generate terrain
 * independently is not a thing that can be tested into existence: any difference in what either of them
 * produces is a silent desync, and silent desyncs surface as bug reports nobody can act on. A client that
 * does not match is told to update.
 *
 * The gate is cheap and it is not optional. The other half of the pair is the per-chunk base hash, which
 * catches the case the gate cannot - a client on the right version whose floating point behaves differently.
 * See [BaseHash].
 */
object VersionGate {

  sealed interface Verdict {
    /** The client may generate its own bases. */
    data object Compatible : Verdict

    /** The client must update, with the reason to show it. */
    data class Incompatible(val reason: String) : Verdict

    /**
     * The client may play, but must be sent fully merged chunks rather than deltas.
     *
     * Only ever returned when the client has opted out of local generation. It is not a fallback for a
     * version mismatch - see the note on this object.
     */
    data object ServerAuthoritativeOnly : Verdict
  }

  /**
   * @param clientGeneratesBase false when the client has not implemented, or has disabled, local base
   *   generation. Such a client is always served merged chunks and needs no version agreement at all.
   */
  fun check(
    server: PipelineVersion,
    client: PipelineVersion,
    clientGeneratesBase: Boolean = true
  ): Verdict {
    if (!clientGeneratesBase) return Verdict.ServerAuthoritativeOnly

    if (client.chunkFormatVersion != server.chunkFormatVersion) {
      return Verdict.Incompatible(
        "chunk format ${client.chunkFormatVersion} cannot read the server's " +
            "${server.chunkFormatVersion}"
      )
    }
    if (client.blockPaletteVersion != server.blockPaletteVersion) {
      return Verdict.Incompatible("the block palette differs; blocks would decode to the wrong material")
    }
    if (client.pipelineVersion != server.pipelineVersion) {
      return Verdict.Incompatible("the world generation pipeline differs; terrain would not match")
    }

    return Verdict.Compatible
  }
}
