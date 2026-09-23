package net.bestia.zone.economy.shop

import net.bestia.bnet.proto.OpenShopCmsgProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

/**
 * A request for what one merchant has, at the prices of wherever the sender is standing.
 *
 * The merchant chooses whose counter, never whose prices: those come from the sender's own position,
 * because a client that could read a distant town's prices turns the merchant profession into a
 * spreadsheet.
 */
data class OpenShopCMSG(
  override val playerId: Long,
  val merchantEntityId: EntityId,
) : CMSG {

  companion object {
    fun fromBnet(playerId: Long, bnet: OpenShopCmsgProto.OpenShopCMSG): OpenShopCMSG {
      return OpenShopCMSG(playerId, bnet.merchantEntityId)
    }
  }
}
