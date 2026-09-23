package net.bestia.zone.economy.shop

import net.bestia.bnet.proto.ShopTradeCmsgProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

/**
 * A request to buy or sell with the settlement the sender is standing in.
 *
 * No price on the wire in either direction. A client that could name its own price would be a client
 * that could be wrong about it - it says how much of what, and the server quotes.
 */
data class ShopTradeCMSG(
  override val playerId: Long,
  val itemId: Long,
  val amount: Int,
  val selling: Boolean,
  val merchantEntityId: EntityId,
) : CMSG {

  companion object {
    fun fromBnet(playerId: Long, bnet: ShopTradeCmsgProto.ShopTradeCMSG): ShopTradeCMSG {
      return ShopTradeCMSG(playerId, bnet.itemId, bnet.amount, bnet.selling, bnet.merchantEntityId)
    }
  }
}
