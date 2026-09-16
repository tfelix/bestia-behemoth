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
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<ShopTradeCMSG> {
  override val handles = ShopTradeCMSG::class

  override fun handle(msg: ShopTradeCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (deadActionGuard.refuses(activeEntityId, "trade with a shop")) {
      return true
    }

    world.modify(activeEntityId) { id ->
      add(id, ShopTradeIntent(itemId = msg.itemId, amount = msg.amount, selling = msg.selling))
    }

    return true
  }
}
