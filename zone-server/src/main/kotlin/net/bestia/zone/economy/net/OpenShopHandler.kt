package net.bestia.zone.economy.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component
import net.bestia.zone.economy.shop.MerchantStock
import net.bestia.zone.economy.shop.ShopCounter

/**
 * Answers with what one merchant has, at the prices of wherever the sender is standing.
 *
 * Unlike [ShopTradeHandler] this needs no intent, because it changes nothing: opening a window is a
 * read, and a read cannot create a settlement's ledger row - see `SettlementEconomyService`.
 */
@Component
class OpenShopHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val merchants: MerchantStock,
  private val shops: ShopCounter,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<OpenShopCMSG> {
  override val wire = decoder(MessageCase.OPEN_SHOP) { accountId, envelope ->
    OpenShopCMSG.fromBnet(accountId, envelope.openShop)
  }

  override fun handle(world: World, msg: OpenShopCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    val counter = merchants.counterOf(msg.merchantEntityId)
    val refusal = if (counter == null) {
      OperationErrorProto.OpError.SHOP_NONE_HERE
    } else {
      shops.open(msg.playerId, activeEntityId, counter)
    }

    if (refusal != null) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(refusal))
    }

    return true
  }
}
