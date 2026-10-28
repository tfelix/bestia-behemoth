package net.bestia.zone.economy.net

import net.bestia.bnet.proto.ShopTradeCmsgProto
import net.bestia.zone.economy.shop.ShopTradeLine
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class ShopTradeCMSG(
  override val playerId: Long,
  val merchantEntityId: EntityId,
  val lines: List<ShopTradeLine>,
  /** Positive when the town pays the player; see `shop_trade_cmsg.proto`. */
  val expectedBalance: Long,
) : CMSG {

  companion object {
    fun fromBnet(playerId: Long, bnet: ShopTradeCmsgProto.ShopTradeCMSG): ShopTradeCMSG {
      return ShopTradeCMSG(playerId, bnet.merchantEntityId, ShopTradeLine.fromBnet(bnet.linesList), bnet.expectedBalance)
    }
  }
}
