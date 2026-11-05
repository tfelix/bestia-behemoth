package net.bestia.zone.postal.net

import net.bestia.zone.message.CMSG

data class DeleteLetterCMSG(
  override val playerId: Long,
  val letterId: Long,
) : CMSG
