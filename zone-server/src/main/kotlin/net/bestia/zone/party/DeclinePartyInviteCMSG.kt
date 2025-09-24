package net.bestia.zone.party

import net.bestia.zone.message.CMSG

data class DeclinePartyInviteCMSG(
  override val playerId: Long,
  val invitationId: Long
) : CMSG
