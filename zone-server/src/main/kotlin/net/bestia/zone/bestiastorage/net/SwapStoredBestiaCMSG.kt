package net.bestia.zone.bestiastorage.net

import net.bestia.bnet.proto.SwapStoredBestiaCmsgProto
import net.bestia.zone.message.CMSG

data class SwapStoredBestiaCMSG(
  override val playerId: Long,
  val storedPlayerBestiaId: Long,
  val activeEntityId: Long,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: SwapStoredBestiaCmsgProto.SwapStoredBestiaCMSG): SwapStoredBestiaCMSG {
      return SwapStoredBestiaCMSG(
        playerId = accountId,
        storedPlayerBestiaId = proto.storedPlayerBestiaId,
        activeEntityId = proto.activeEntityId,
      )
    }
  }
}
