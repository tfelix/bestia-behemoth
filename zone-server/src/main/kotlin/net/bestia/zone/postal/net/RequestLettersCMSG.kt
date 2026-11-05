package net.bestia.zone.postal.net

import net.bestia.zone.message.CMSG

data class RequestLettersCMSG(
  override val playerId: Long,
) : CMSG
