package net.bestia.zone.party.net

import net.bestia.bnet.proto.PartyInfoSmsgProto
import net.bestia.bnet.proto.SetPartyExpShareCmsgProto
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.message.CMSG

data class SetPartyExpShareCMSG(
  override val playerId: Long,
  /** Null for a value this server does not know. */
  val expShare: PartyExpShare?
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: SetPartyExpShareCmsgProto.SetPartyExpShareCMSG): SetPartyExpShareCMSG {
      val expShare = when (proto.expShare) {
        PartyInfoSmsgProto.PartyExpShare.PARTY_EXP_SHARE_EACH_TAKE -> PartyExpShare.EACH_TAKE
        PartyInfoSmsgProto.PartyExpShare.PARTY_EXP_SHARE_EVEN_SHARE -> PartyExpShare.EVEN_SHARE
        else -> null
      }

      return SetPartyExpShareCMSG(playerId = accountId, expShare = expShare)
    }
  }
}
