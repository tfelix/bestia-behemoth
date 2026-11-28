package net.bestia.zone.bestia.net

import net.bestia.zone.message.CMSG

data class GetBestiaDiaryCMSG(
  override val playerId: Long,
) : CMSG
