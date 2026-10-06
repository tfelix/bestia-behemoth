package net.bestia.zone.master.net

import net.bestia.zone.message.CMSG

data class GetSelfCMSG(
  override val playerId: Long
) : CMSG