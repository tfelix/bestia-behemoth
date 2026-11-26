package net.bestia.zone.party.net

import net.bestia.bnet.proto.PartyInfoSmsgProto
import net.bestia.bnet.proto.SetPartyLootShareCmsgProto
import net.bestia.zone.identity.ecs.PartyLootShare
import net.bestia.zone.message.CMSG

data class SetPartyLootShareCMSG(
  override val playerId: Long,
  /** Null for a value this server does not know. */
  val lootShare: PartyLootShare?
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: SetPartyLootShareCmsgProto.SetPartyLootShareCMSG): SetPartyLootShareCMSG {
      val lootShare = when (proto.lootShare) {
        PartyInfoSmsgProto.PartyLootShare.PARTY_LOOT_SHARE_PARTY_SHARE -> PartyLootShare.PARTY_SHARE
        PartyInfoSmsgProto.PartyLootShare.PARTY_LOOT_SHARE_TAKE_TURNS -> PartyLootShare.TAKE_TURNS
        else -> null
      }

      return SetPartyLootShareCMSG(playerId = accountId, lootShare = lootShare)
    }
  }
}
