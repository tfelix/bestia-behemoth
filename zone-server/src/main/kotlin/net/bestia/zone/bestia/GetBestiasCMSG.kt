package net.bestia.zone.bestia

import net.bestia.zone.message.CMSG

data class GetBestiasCMSG(
  override val playerId: Long
) : CMSG