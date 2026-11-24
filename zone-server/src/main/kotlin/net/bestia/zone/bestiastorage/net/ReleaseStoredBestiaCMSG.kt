package net.bestia.zone.bestiastorage.net

import net.bestia.bnet.proto.ReleaseStoredBestiaCmsgProto
import net.bestia.zone.message.CMSG

data class ReleaseStoredBestiaCMSG(
  override val playerId: Long,
  val playerBestiaId: Long,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: ReleaseStoredBestiaCmsgProto.ReleaseStoredBestiaCMSG): ReleaseStoredBestiaCMSG {
      return ReleaseStoredBestiaCMSG(
        playerId = accountId,
        playerBestiaId = proto.playerBestiaId,
      )
    }
  }
}
