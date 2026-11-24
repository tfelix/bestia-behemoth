package net.bestia.zone.bestiastorage.net

import net.bestia.bnet.proto.SummonStoredBestiaCmsgProto
import net.bestia.zone.message.CMSG

data class SummonStoredBestiaCMSG(
  override val playerId: Long,
  val playerBestiaId: Long,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: SummonStoredBestiaCmsgProto.SummonStoredBestiaCMSG): SummonStoredBestiaCMSG {
      return SummonStoredBestiaCMSG(
        playerId = accountId,
        playerBestiaId = proto.playerBestiaId,
      )
    }
  }
}
