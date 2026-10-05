package net.bestia.zone.party

import net.bestia.bnet.proto.RequestPartyInfoCmsgProto
import net.bestia.zone.message.CMSG

/**
 * Requests more detailed information of the party like the listed
 * entities and their IDs.
 */
data class RequestPartyInfoCMSG(
  override val playerId: Long
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: RequestPartyInfoCmsgProto.RequestPartyInfoCMSG): RequestPartyInfoCMSG {
      return RequestPartyInfoCMSG(playerId = accountId)
    }
  }
}
