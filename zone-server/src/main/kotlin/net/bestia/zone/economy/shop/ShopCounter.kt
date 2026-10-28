package net.bestia.zone.economy.shop

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.Shop
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Service

/**
 * Opening a merchant's window: whether they are at work, whether the player stands at their counter, and
 * which town's prices apply. Every way into a shop - talking, asking in conversation, a direct request -
 * comes through here, so they cannot disagree about when a shop is open.
 */
@Service
class ShopCounter(
  private val world: WorldView,
  private val economy: SettlementEconomyService,
  private val offers: ShopOfferPublisher,
  private val commodities: CommodityItems,
  private val out: OutMessageProcessor,
  private val clock: BestiaClock,
) {

  fun isOpen(counter: Counter): Boolean {
    return counter.hours.isOpenAt(clock.now().minuteOfDay)
  }

  /** Pushes the window to [accountId]. @return why it could not, or null once it is on its way. */
  fun open(accountId: AccountId, viewerId: EntityId, counter: Counter): OpError? {
    if (!isOpen(counter)) return OpError.SHOP_CLOSED
    val (settlement, shop) = shopFor(viewerId, counter) ?: return OpError.SHOP_NONE_HERE

    offers.publishTo(accountId, settlement, shop, counter)

    return null
  }

  /** Quotes [lines] without trading, and answers with a [ShopQuoteSMSG]. */
  fun quote(accountId: AccountId, viewerId: EntityId, counter: Counter, lines: List<ShopTradeLine>) {
    if (!isOpen(counter)) return refuse(accountId, counter, OpError.SHOP_CLOSED)
    val (_, shop) = shopFor(viewerId, counter) ?: return refuse(accountId, counter, OpError.SHOP_NONE_HERE)

    val quote = shop.quoteBasket(ShopTradeLine.basketOf(lines, counter, commodities))
    val answer = ShopQuoteSMSG(
      merchantEntityId = counter.merchantId,
      lineCoins = quote.lineCoins,
      balance = quote.balance,
      refusal = quote.refusal?.let { codeFor(it) },
      refusedLine = quote.refusedLine,
    )
    out.sendToPlayer(accountId, answer)
  }

  /** Prices are the town's the player stands in, never the merchant's: the merchant only chooses the counter. */
  fun shopFor(viewerId: EntityId, counter: Counter): Pair<Int, Shop>? {
    return world.read {
      val position = get(viewerId, Position::class)?.toVec3L() ?: return@read null
      val merchant = get(counter.merchantId, Position::class)?.toVec3L() ?: return@read null

      if (ShopReach.withinReach(position, merchant)) economy.shopAt(position.x, position.y) else null
    }
  }

  private fun refuse(accountId: AccountId, counter: Counter, code: OpError) {
    out.sendToPlayer(accountId, ShopQuoteSMSG(counter.merchantId, emptyList(), 0, code))
  }

  companion object {
    fun codeFor(refusal: Shop.Refusal): OpError {
      return when (refusal) {
        Shop.Refusal.NOT_STOCKED -> OpError.SHOP_NOT_STOCKED
        Shop.Refusal.OUT_OF_STOCK -> OpError.SHOP_OUT_OF_STOCK
        Shop.Refusal.TREASURY_EMPTY -> OpError.SHOP_TREASURY_EMPTY
        Shop.Refusal.TREASURY_FULL -> OpError.SHOP_TREASURY_FULL
      }
    }
  }
}
