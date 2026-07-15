package net.bestia.zone.ecs.logout

import net.bestia.bnet.proto.RequestLogoutCmsgProto
import net.bestia.zone.message.CMSG

/**
 * Client requests the (delayed, cancellable) logout countdown to start for its active master.
 */
data class RequestLogoutCMSG(
  override val playerId: Long
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, @Suppress("UNUSED_PARAMETER") proto: RequestLogoutCmsgProto.RequestLogoutCMSG): RequestLogoutCMSG =
      RequestLogoutCMSG(playerId = accountId)
  }
}
