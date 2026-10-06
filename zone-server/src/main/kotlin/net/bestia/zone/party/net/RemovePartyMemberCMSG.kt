package net.bestia.zone.party.net

import net.bestia.bnet.proto.RemovePartyMemberCmsgProto
import net.bestia.zone.message.CMSG

data class RemovePartyMemberCMSG(
  override val playerId: Long,
  val partyId: Long,
  val memberAccountId: Long
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: RemovePartyMemberCmsgProto.RemovePartyMemberCMSG): RemovePartyMemberCMSG {
      return RemovePartyMemberCMSG(playerId = accountId, partyId = proto.partyId, memberAccountId = proto.memberAccountId)
    }
  }
}
