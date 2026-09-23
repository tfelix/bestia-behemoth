package net.bestia.zone.economy.shop

import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.World
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.Shop
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Turns a [Shop] into the window one player sees.
 *
 * Its own bean because two callers need it and they run on different threads: the handler answers
 * "open the shop" off the tick, and the trade system re-sends the window after every trade so the price
 * a player just moved is the price they are looking at.
 */
@Component
class ShopOfferPublisher(
  private val commodities: CommodityItems,
  private val outMessageProcessor: OutMessageProcessor,
) {

  fun publish(world: World, viewerId: EntityId, settlement: Int, shop: Shop, stocked: Set<String>) {
    val accountId = world.get(viewerId, Account::class)?.accountId ?: return

    publishTo(accountId, settlement, shop, stocked)
  }

  /** @param stocked what this merchant deals in; the town may hold plenty the counter has never had. */
  fun publishTo(accountId: Long, settlement: Int, shop: Shop, stocked: Set<String>) {
    val entries = shop.offers().filter { it.commodity.id in stocked }.mapNotNull { offer ->
      // A commodity whose item never made it into the catalogue is dropped rather than sent with a
      // meaningless id. `EconomyCoverage` fails the boot over it, so this cannot happen in a booted
      // server - what it protects is a test wiring a partial catalogue.
      commodities.itemIdOf(offer.commodity.id)?.let {
        ShopOfferSMSG.Entry(it, offer.offered, offer.buyPrice, offer.sellPrice)
      }
    }

    outMessageProcessor.sendToPlayer(accountId, ShopOfferSMSG(settlement, entries))
  }
}
