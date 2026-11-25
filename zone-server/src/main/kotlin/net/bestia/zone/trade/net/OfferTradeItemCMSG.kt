package net.bestia.zone.trade.net

import net.bestia.bnet.proto.OfferTradeItemCmsgProto
import net.bestia.bnet.proto.OfferTradeItemCmsgProto.OfferTradeItemCMSG.OfferCase
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

/** One of [playerItemId] and [bestiaEntityId] is set: an item from the bag, or a party bestia. */
data class OfferTradeItemCMSG(
  override val playerId: Long,
  val tradeId: Long,
  val playerItemId: Long? = null,
  val amount: Int = 0,
  val bestiaEntityId: EntityId? = null,
  val acceptCarriedItems: Boolean = false,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: OfferTradeItemCmsgProto.OfferTradeItemCMSG): OfferTradeItemCMSG {
      return OfferTradeItemCMSG(
        playerId = accountId,
        tradeId = msg.tradeId,
        playerItemId = msg.playerItemId.takeIf { msg.offerCase == OfferCase.PLAYER_ITEM_ID },
        amount = msg.amount,
        bestiaEntityId = msg.bestiaEntityId.takeIf { msg.offerCase == OfferCase.BESTIA_ENTITY_ID },
        acceptCarriedItems = msg.acceptCarriedItems,
      )
    }
  }
}
