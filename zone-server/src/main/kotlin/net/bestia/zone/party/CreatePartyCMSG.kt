package net.bestia.zone.party

import net.bestia.zone.message.CMSG

data class CreatePartyCMSG(
  override val playerId: Long,
  val partyName: String
) : CMSG