package net.bestia.zone.party

import net.bestia.bnet.proto.AcceptPartyInviteCmsgProto
import net.bestia.zone.message.CMSG

data class AcceptPartyInviteCMSG(
  override val playerId: Long,
  val invitationId: Long
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: AcceptPartyInviteCmsgProto.AcceptPartyInviteCMSG): AcceptPartyInviteCMSG =
      AcceptPartyInviteCMSG(playerId = accountId, invitationId = proto.invitationId)
  }
}
