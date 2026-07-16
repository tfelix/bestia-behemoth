package net.bestia.zone.party

import net.bestia.bnet.proto.DeclinePartyInviteCmsgProto
import net.bestia.zone.message.CMSG

data class DeclinePartyInviteCMSG(
  override val playerId: Long,
  val invitationId: Long
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: DeclinePartyInviteCmsgProto.DeclinePartyInviteCMSG): DeclinePartyInviteCMSG =
      DeclinePartyInviteCMSG(playerId = accountId, invitationId = proto.invitationId)
  }
}
