package net.bestia.zone.master.net

import net.bestia.zone.message.CMSG

data class DeleteOrderPresetCMSG(
  override val playerId: Long,
  val presetId: Long,
) : CMSG
