package net.bestia.zone.economy.shop

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.economy.SettlementEconomyService
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
  private val clock: BestiaClock,
) {

  fun isOpen(counter: Counter): Boolean {
    return counter.hours.isOpenAt(clock.now().minuteOfDay)
  }

  /** Pushes the window to [accountId]. @return why it could not, or null once it is on its way. */
  fun open(accountId: AccountId, viewerId: EntityId, counter: Counter): OpError? {
    if (!isOpen(counter)) return OpError.SHOP_CLOSED

    // Prices are the town's the player stands in, never the merchant's: the merchant only chooses the counter.
    val shop = world.read {
      val position = get(viewerId, Position::class)?.toVec3L() ?: return@read null
      val merchant = get(counter.merchantId, Position::class)?.toVec3L() ?: return@read null

      if (ShopReach.withinReach(position, merchant)) economy.shopAt(position.x, position.y) else null
    } ?: return OpError.SHOP_NONE_HERE

    offers.publishTo(accountId, shop.first, shop.second, counter)

    return null
  }
}
