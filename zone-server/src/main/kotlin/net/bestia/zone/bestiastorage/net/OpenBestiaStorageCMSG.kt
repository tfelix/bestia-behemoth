package net.bestia.zone.bestiastorage.net

import net.bestia.bnet.proto.OpenBestiaStorageCmsgProto
import net.bestia.zone.message.CMSG

data class OpenBestiaStorageCMSG(
  override val playerId: Long,
) : CMSG {
  companion object {
    fun fromBnet(
      accountId: Long,
      @Suppress("UNUSED_PARAMETER") proto: OpenBestiaStorageCmsgProto.OpenBestiaStorageCMSG
    ): OpenBestiaStorageCMSG {
      return OpenBestiaStorageCMSG(playerId = accountId)
    }
  }
}
