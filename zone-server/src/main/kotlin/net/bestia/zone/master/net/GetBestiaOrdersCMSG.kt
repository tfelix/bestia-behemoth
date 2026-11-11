package net.bestia.zone.master.net

import net.bestia.zone.message.CMSG
import net.bestia.zone.util.PlayerBestiaId

data class GetBestiaOrdersCMSG(
  override val playerId: Long,
  val playerBestiaId: PlayerBestiaId,
) : CMSG
