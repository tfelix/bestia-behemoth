package net.bestia.zone.party

import net.bestia.bnet.proto.RequestPartyInvitationCmsgProto
import net.bestia.zone.message.CMSG

data class RequestPartyInvitationCMSG(
  override val playerId: Long,
  val invitedAccountId: Long
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: RequestPartyInvitationCmsgProto.RequestPartyInvitationCMSG): RequestPartyInvitationCMSG {
      return RequestPartyInvitationCMSG(playerId = accountId, invitedAccountId = proto.invitedAccountId)
    }
  }
}
