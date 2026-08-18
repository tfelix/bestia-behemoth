package net.bestia.zone.cartography.chart

import net.bestia.bnet.proto.MergeChartsCmsgProto
import net.bestia.zone.message.CMSG

/**
 * The player asked for two held charts to be joined into one.
 *
 * Not a skill activation. `CARTOGRAPHY` makes charts and nothing else, so joining two needs only the two items -
 * which is what lets a player who has never surveyed anything buy a chart and add it to theirs.
 */
data class MergeChartsCMSG(
  override val playerId: Long,

  /** The chart that stays, and ends up holding both surveys. */
  val intoUniqueId: Long,

  /** The chart that is consumed. */
  val fromUniqueId: Long
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, merge: MergeChartsCmsgProto.MergeChartsCMSG): MergeChartsCMSG =
      MergeChartsCMSG(
        playerId = accountId,
        intoUniqueId = merge.intoUniqueId,
        fromUniqueId = merge.fromUniqueId
      )
  }
}
