package net.bestia.zone.cartography.chart

import net.bestia.bnet.proto.CopyChartCmsgProto
import net.bestia.zone.message.CMSG

/** The player asked for a held chart to be copied onto a blank, which is how a chart becomes sellable. */
data class CopyChartCMSG(
  override val playerId: Long,
  val uniqueId: Long
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, copy: CopyChartCmsgProto.CopyChartCMSG): CopyChartCMSG =
      CopyChartCMSG(playerId = accountId, uniqueId = copy.uniqueId)
  }
}
