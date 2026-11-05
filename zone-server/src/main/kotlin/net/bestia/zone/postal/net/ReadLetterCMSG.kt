package net.bestia.zone.postal.net

import net.bestia.zone.message.CMSG

data class ReadLetterCMSG(
  override val playerId: Long,
  val letterId: Long,
) : CMSG
