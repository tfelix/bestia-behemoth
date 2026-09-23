package net.bestia.zone.economy.shop

import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.economy.ShopTradeIntent
import net.bestia.zone.message.InMessageProcessor
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
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<ShopTradeCMSG> {
  override val handles = ShopTradeCMSG::class

  override fun handle(msg: ShopTradeCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (deadActionGuard.refuses(activeEntityId, "trade with a shop")) {
      return true
    }

    // Resolved here because `SpeakerResolver` takes the world lock, and the system that reads this runs
    // holding it. An empty set is a refusal the system reports, rather than a silent no-op here.
    val stocked = merchants.of(msg.merchantEntityId).orEmpty()

    world.modify(activeEntityId) { id ->
      add(
        id,
        ShopTradeIntent(
          itemId = msg.itemId,
          amount = msg.amount,
          selling = msg.selling,
          stocked = stocked,
        )
      )
    }

    return true
  }
}
