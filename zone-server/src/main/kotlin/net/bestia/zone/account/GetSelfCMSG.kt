package net.bestia.zone.account

import net.bestia.zone.message.CMSG

data class GetSelfCMSG(
  override val playerId: Long
) : CMSG