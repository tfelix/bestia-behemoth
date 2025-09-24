package net.bestia.zone.party

import net.bestia.zone.message.CMSG

data class RequestPartyInvitationCMSG(
  override val playerId: Long,
  val invitedPlayerEntityId: Long
) : CMSG