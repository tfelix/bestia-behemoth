package net.bestia.zone.master.net

import net.bestia.zone.message.CMSG

data class GetOrderBookCMSG(
  override val playerId: Long,
) : CMSG
