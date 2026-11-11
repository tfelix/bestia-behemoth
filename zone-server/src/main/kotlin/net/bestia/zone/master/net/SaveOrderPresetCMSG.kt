package net.bestia.zone.master.net

import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.message.CMSG

/** [orders] is null when the sheet names something this server does not know or breaks a rule of [AiConfig]. */
data class SaveOrderPresetCMSG(
  override val playerId: Long,
  val presetId: Long?,
  val name: String,
  val orders: AiConfig?,
) : CMSG
