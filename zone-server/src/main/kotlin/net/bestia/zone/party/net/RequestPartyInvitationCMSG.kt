package net.bestia.zone.party.net

import net.bestia.bnet.proto.RequestPartyInvitationCmsgProto
import net.bestia.zone.message.CMSG

data class RequestPartyInvitationCMSG(
  override val playerId: Long,
  val invitedMasterName: String
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: RequestPartyInvitationCmsgProto.RequestPartyInvitationCMSG): RequestPartyInvitationCMSG {
      return RequestPartyInvitationCMSG(playerId = accountId, invitedMasterName = proto.invitedMasterName)
    }
  }
}
