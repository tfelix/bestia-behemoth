package net.bestia.zone.economy.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.economy.shop.MerchantStock
import net.bestia.zone.economy.shop.ShopCounter
import net.bestia.zone.economy.shop.ShopTradeLine
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Quotes a basket. A read, so no intent: on the tick only because the market may be read nowhere else. */
@Component
class ShopQuoteHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val merchants: MerchantStock,
  private val shops: ShopCounter,
) : TickMessageHandler<ShopQuoteCMSG> {
  override val wire = decoder(MessageCase.SHOP_QUOTE) { accountId, envelope ->
    ShopQuoteCMSG.fromBnet(accountId, envelope.shopQuote)
  }

  override fun handle(world: World, msg: ShopQuoteCMSG): Boolean {
    if (!ShopTradeLine.isWellFormed(msg.lines)) return true
    val counter = merchants.counterOf(msg.merchantEntityId) ?: return true

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)
    shops.quote(msg.playerId, activeEntityId, counter, msg.lines)

    return true
  }
}
