package net.bestia.zone.world.stream

import net.bestia.bnet.proto.SurfacePatchRequestCMSGProto
import net.bestia.worldgen.lod.PatchPos
import net.bestia.zone.message.CMSG

/**
 * The client asking for coarse patches it does not already hold.
 *
 * Every position in here is untrusted and is checked against this account's own announced set before
 * anything is sampled or sent - see [SurfacePatchRequestHandler] and [ChunkStreamSystem.servePatchRequests].
 */
data class SurfacePatchRequestCMSG(
  override val playerId: Long,
  val patches: List<PatchPos>
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, request: SurfacePatchRequestCMSGProto.SurfacePatchRequestCMSG) =
      SurfacePatchRequestCMSG(
        playerId = accountId,
        patches = request.patchesList.map { SurfacePatchCoords.fromProto(it) }
      )
  }
}
