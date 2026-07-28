package net.bestia.zone.world.stream

import net.bestia.bnet.proto.ChunkRequestCMSGProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.CMSG

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
    fun fromBnet(accountId: Long, request: ChunkRequestCMSGProto.ChunkRequestCMSG) = ChunkRequestCMSG(
      playerId = accountId,
      chunks = request.chunksList.map { ChunkCoords.fromProto(it) }
    )
  }
}
