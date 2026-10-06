package net.bestia.zone.world.net

import net.bestia.bnet.proto.ChunkRequestCMSGProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.CMSG
import net.bestia.zone.world.stream.ChunkCoords

/**
 * The client asking for chunk payloads it does not hold at the announced revision.
 *
 * Every position in here is untrusted and is checked against this account's own announced set before
 * anything is generated or read - see [ChunkRequestHandler].
 */
data class ChunkRequestCMSG(
  override val playerId: Long,
  val chunks: List<ChunkPos>
) : CMSG {

  companion object {
    /** More than twice the largest view volume, 11 x 11 x 3 chunks; anything beyond it is forged work. */
    const val MAX_CHUNKS_PER_REQUEST = 1024

    fun fromBnet(accountId: Long, request: ChunkRequestCMSGProto.ChunkRequestCMSG): ChunkRequestCMSG {
      return ChunkRequestCMSG(
        playerId = accountId,
        chunks = request.chunksList.take(MAX_CHUNKS_PER_REQUEST).map { ChunkCoords.fromProto(it) }
      )
    }
  }
}
