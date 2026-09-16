package net.bestia.zone.economy.shop

import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/**
 * Answers with the prices of wherever the sender is standing.
 *
 * Unlike [ShopTradeHandler] this needs no intent, because it changes nothing: opening a window is a
 * read, and a read cannot create a settlement's ledger row - see `SettlementEconomyService`. The world
 * lock is taken for the position and the catch-up, which is what a `WorldView.read` gives.
 */
@Component
class OpenShopHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val economy: SettlementEconomyService,
  private val offers: ShopOfferPublisher,
  private val outMessageProcessor: OutMessageProcessor,
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<OpenShopCMSG> {
  override val handles = OpenShopCMSG::class

  override fun handle(msg: OpenShopCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    val shop = world.read {
      val position = get(activeEntityId, Position::class)?.toVec3L()
      position?.let { economy.shopAt(it.x, it.y) }
    }

    if (shop == null) {
      outMessageProcessor.sendToPlayer(
        msg.playerId,
        OperationErrorSMSG(OperationErrorProto.OpError.SHOP_NONE_HERE)
      )
      return true
    }

    offers.publishTo(msg.playerId, shop.first, shop.second)

    return true
  }
}
