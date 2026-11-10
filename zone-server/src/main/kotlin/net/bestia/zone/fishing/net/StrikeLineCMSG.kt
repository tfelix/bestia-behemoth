package net.bestia.zone.fishing.net

import net.bestia.zone.message.CMSG

/** The active master pulls the line in, hoping a fish has the hook. */
data class StrikeLineCMSG(
  override val playerId: Long,
) : CMSG
