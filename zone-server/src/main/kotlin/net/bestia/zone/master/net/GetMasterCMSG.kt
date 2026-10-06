package net.bestia.zone.master.net

import net.bestia.zone.message.CMSG

data class GetMasterCMSG(
  override val playerId: Long
) : CMSG