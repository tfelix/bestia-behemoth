package net.bestia.zone.party

import net.bestia.zone.message.CMSG

data class RemovePartyMemberCMSG(
  override val playerId: Long,
  val partyId: Long,
  val memberAccountId: Long
) : CMSG