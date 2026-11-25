package net.bestia.zone.trade.net

import net.bestia.bnet.proto.RetractTradeItemCmsgProto
import net.bestia.bnet.proto.RetractTradeItemCmsgProto.RetractTradeItemCMSG.LineCase
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.PlayerBestiaId

/** One of [offerSlotId] and [playerBestiaId] is set: an item line, or a bestia line. */
data class RetractTradeItemCMSG(
  override val playerId: Long,
  val tradeId: Long,
  val offerSlotId: Long? = null,
  val playerBestiaId: PlayerBestiaId? = null,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: RetractTradeItemCmsgProto.RetractTradeItemCMSG): RetractTradeItemCMSG {
      return RetractTradeItemCMSG(
        playerId = accountId,
        tradeId = msg.tradeId,
        offerSlotId = msg.offerSlotId.takeIf { msg.lineCase == LineCase.OFFER_SLOT_ID },
        playerBestiaId = msg.playerBestiaId.takeIf { msg.lineCase == LineCase.PLAYER_BESTIA_ID },
      )
    }
  }
}
