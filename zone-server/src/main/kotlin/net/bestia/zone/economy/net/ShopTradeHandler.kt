package net.bestia.zone.economy.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.economy.ecs.ShopTradeIntent
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component
import net.bestia.zone.economy.shop.MerchantStock
import net.bestia.zone.economy.shop.ShopCounter
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor

/**
 * Attaches a [ShopTradeIntent] and returns. As empty as `CollectPropHandler`, and for its reason: every
 * check happens on the tick thread, because the ledger the trade writes is only safe there.
 */
@Component
class ShopTradeHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val deadActionGuard: DeadActionGuard,
  private val merchants: MerchantStock,
  private val shops: ShopCounter,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<ShopTradeCMSG> {
  override val wire = decoder(MessageCase.SHOP_TRADE) { accountId, envelope ->
    ShopTradeCMSG.fromBnet(accountId, envelope.shopTrade)
  }

  override fun handle(world: World, msg: ShopTradeCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (deadActionGuard.refuses(world, activeEntityId, "trade with a shop")) {
      return true
    }

    // Resolved here because `SpeakerResolver` opens a world scope of its own, which the system that reads
    // this has no use for.
    val counter = merchants.counterOf(msg.merchantEntityId)
      ?: return deny(msg, OperationErrorProto.OpError.SHOP_NONE_HERE)
    if (!shops.isOpen(counter)) {
      return deny(msg, OperationErrorProto.OpError.SHOP_CLOSED)
    }

    world.modify(activeEntityId) { id ->
      add(
        id,
        ShopTradeIntent(
          lines = msg.lines,
          expectedBalance = msg.expectedBalance,
          counter = counter,
        )
      )
    }

    return true
  }

  private fun deny(msg: ShopTradeCMSG, code: OperationErrorProto.OpError): Boolean {
    outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(code))

    return true
  }
}
