package net.bestia.zone.economy.net

import net.bestia.bnet.proto.ShopQuoteCmsgProto
import net.bestia.zone.economy.shop.ShopTradeLine
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class ShopQuoteCMSG(
  override val playerId: Long,
  val merchantEntityId: EntityId,
  val lines: List<ShopTradeLine>,
) : CMSG {

  companion object {
    fun fromBnet(playerId: Long, bnet: ShopQuoteCmsgProto.ShopQuoteCMSG): ShopQuoteCMSG {
      return ShopQuoteCMSG(playerId, bnet.merchantEntityId, ShopTradeLine.fromBnet(bnet.linesList))
    }
  }
}
