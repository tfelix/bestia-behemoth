package net.bestia.zone.party

import net.bestia.bnet.proto.RequestDisbandPartyCmsgProto
import net.bestia.zone.message.CMSG

data class RequestDisbandPartyCMSG(
  override val playerId: Long,
  val partyId: Long
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: RequestDisbandPartyCmsgProto.RequestDisbandPartyCMSG): RequestDisbandPartyCMSG {
      return RequestDisbandPartyCMSG(playerId = accountId, partyId = proto.partyId)
    }
  }
}
