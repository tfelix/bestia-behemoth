package net.bestia.zone.party

import net.bestia.zone.message.CMSG

data class RequestDisbandPartyCMSG(
  override val playerId: Long,
  val partyId: Long
) : CMSG