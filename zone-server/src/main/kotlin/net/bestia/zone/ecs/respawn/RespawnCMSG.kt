package net.bestia.zone.ecs.respawn

import net.bestia.bnet.proto.RespawnCmsgProto
import net.bestia.zone.message.CMSG

/**
 * Client asks for its currently active entity to be revived at its save point.
 */
data class RespawnCMSG(
  override val playerId: Long
) : CMSG {

  companion object {
    fun fromBnet(
      accountId: Long,
      @Suppress("UNUSED_PARAMETER") proto: RespawnCmsgProto.RespawnCMSG
    ): RespawnCMSG {
      return RespawnCMSG(playerId = accountId)
    }
  }
}
