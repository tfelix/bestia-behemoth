package net.bestia.zone.economy.shop

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.economy.ShopTradeIntent
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/**
 * Attaches a [ShopTradeIntent] and returns. As empty as `CollectPropHandler`, and for its reason: every
 * check happens on the tick thread, because the ledger the trade writes is only safe there.
 */
@Component
class ShopTradeHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val deadActionGuard: DeadActionGuard,
  private val merchants: MerchantStock,
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
    // this has no use for. An empty set is a refusal the system reports, rather than a silent no-op here.
    val stocked = merchants.of(msg.merchantEntityId).orEmpty()

    world.modify(activeEntityId) { id ->
      add(
        id,
        ShopTradeIntent(
          itemId = msg.itemId,
          amount = msg.amount,
          selling = msg.selling,
          stocked = stocked,
          merchantEntityId = msg.merchantEntityId,
        )
      )
    }

    return true
  }
}
