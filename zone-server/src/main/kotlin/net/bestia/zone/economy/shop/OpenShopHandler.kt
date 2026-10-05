package net.bestia.zone.economy.shop

import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import org.springframework.stereotype.Component

/**
 * Answers with what one merchant has, at the prices of wherever the sender is standing.
 *
 * Unlike [ShopTradeHandler] this needs no intent, because it changes nothing: opening a window is a
 * read, and a read cannot create a settlement's ledger row - see `SettlementEconomyService`.
 *
 * The merchant is re-checked for distance rather than trusted. A conversation has already established
 * they are in earshot, but a hand-made packet has had no conversation.
 */
@Component
class OpenShopHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val economy: SettlementEconomyService,
  private val merchants: MerchantStock,
  private val offers: ShopOfferPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<OpenShopCMSG> {
  override val handles = OpenShopCMSG::class

  override fun handle(world: World, msg: OpenShopCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    val stocked = merchants.of(msg.merchantEntityId)

    val shop = if (stocked == null) {
      null
    } else {
      with(world) {
        val position = get(activeEntityId, Position::class)?.toVec3L()
        val counter = get(msg.merchantEntityId, Position::class)?.toVec3L()

        if (position == null || counter == null || !ShopReach.withinReach(position, counter)) {
          null
        } else {
          economy.shopAt(position.x, position.y)
        }
      }
    }

    if (shop == null || stocked == null) {
      outMessageProcessor.sendToPlayer(
        msg.playerId,
        OperationErrorSMSG(OperationErrorProto.OpError.SHOP_NONE_HERE)
      )
      return true
    }

    offers.publishTo(msg.playerId, msg.merchantEntityId, shop.first, shop.second, stocked)

    return true
  }
}
