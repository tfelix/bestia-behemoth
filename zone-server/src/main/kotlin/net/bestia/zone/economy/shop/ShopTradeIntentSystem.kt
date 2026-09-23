package net.bestia.zone.economy.shop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.economy.ShopTradeIntent
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.item.ObtainItemIntent
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.economy.Commodity
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.Shop
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Resolves a [ShopTradeIntent]: quotes the trade against the town the player is standing in, moves the
 * coin and the goods, and sends the window back so the new price is visible.
 *
 * ### @Order(63)
 *
 * Immediately before `CollectPropIntentSystem` at 64, which it mirrors, and for that system's reasons:
 * before `DeathSystem` (@70) and `PersistAndRemoveSystem` (@90), because `World.addNow` requires its
 * target alive and the deferred queue is FIFO - running later would let a player who dies in the same
 * tick they buy blow up `applyDeferred` on the tick thread.
 *
 * ### Which side of a crash a failure falls on
 *
 * The goods are granted through [ObtainItemIntent.CreateItemIntent], which is durable, and the coin is
 * taken through the same durable path a craft takes its materials through. Only the *ledger* rides the
 * periodic save. So the worst a crash can cost is under a minute of one settlement's trades replayed as
 * though they had not happened - invisible, and self-correcting, because prices reconverge.
 *
 * The ordering within a sale is deliberate: the goods leave the player before the coin arrives. A crash
 * in that window costs the seller their goods, which is bad; the other order would mint coin, which is
 * the one duplication that actually matters.
 */
@SpringComponent
@Order(63)
class ShopTradeIntentSystem(
  private val economy: SettlementEconomyService,
  private val commodities: CommodityItems,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val offers: ShopOfferPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : System {

  override val reads: ComponentClassSet = setOf(Account::class, Master::class, Position::class)

  /**
   * [ObtainItemIntent.CreateItemIntent] is declared although it lands on the same entity this queries -
   * it is what keeps this out of `ObtainItemIntentSystem`'s scheduler wave. `Inventory` is written
   * directly, so the second player of a pair sees the first player's coin already gone.
   */
  override val writes: ComponentClassSet = setOf(
    ShopTradeIntent::class, ObtainItemIntent.CreateItemIntent::class, Inventory::class
  )

  override fun update(world: World, deltaTime: Float) {
    world.query(ShopTradeIntent::class).each { traderId ->
      val intent = get<ShopTradeIntent>()
      trade(world, traderId, intent)
      world.remove(traderId, ShopTradeIntent::class)
    }
  }

  private fun trade(world: World, traderId: EntityId, intent: ShopTradeIntent) {
    if (intent.amount !in 1..MAX_UNITS_PER_TRADE) return

    val position = world.get(traderId, Position::class)?.toVec3L()
      ?: return deny(world, traderId, OperationErrorProto.OpError.SHOP_NONE_HERE)
    val (settlement, shop) = economy.shopAt(position.x, position.y)
      ?: return deny(world, traderId, OperationErrorProto.OpError.SHOP_NONE_HERE)

    val commodity = commodities.commodityOf(intent.itemId)
      ?.takeIf { it.id in intent.stocked }
    val quote = if (intent.selling) {
      shop.quoteSell(commodity, intent.amount)
    } else {
      shop.quoteBuy(commodity, intent.amount)
    }

    quote.refusal?.let { return deny(world, traderId, codeFor(it)) }
    // Only ever null when the quote was refused, and the line above returned.
    val good = commodity ?: return

    val moved = if (intent.selling) {
      sell(world, traderId, good, intent.amount, quote.coins)
    } else {
      buy(world, traderId, good, intent.amount, quote.coins)
    }
    if (!moved) return deny(world, traderId, OperationErrorProto.OpError.SHOP_CANNOT_AFFORD)

    economy.settle(settlement, good.id, intent.amount, quote.coins, intent.selling)
    offers.publish(world, traderId, settlement, shop, intent.stocked)

    LOG.debug {
      "Entity $traderId ${if (intent.selling) "sold" else "bought"} ${intent.amount} ${good.id} " +
        "for ${quote.coins} in settlement $settlement"
    }
  }

  private fun buy(world: World, buyerId: EntityId, good: Commodity, units: Int, coins: Long): Boolean {
    val coinItem = commodities.coinItemId() ?: return false
    if (!takeFromInventory(world, buyerId, coinItem, coins.toInt())) return false

    world.add(buyerId, ObtainItemIntent.CreateItemIntent(itemIdOf(good) ?: return false, units))

    return true
  }

  private fun sell(world: World, sellerId: EntityId, good: Commodity, units: Int, coins: Long): Boolean {
    val itemId = itemIdOf(good) ?: return false
    if (!takeFromInventory(world, sellerId, itemId, units)) return false

    val coinItem = commodities.coinItemId() ?: return false
    world.add(sellerId, ObtainItemIntent.CreateItemIntent(coinItem, coins.toInt()))

    return true
  }

  /**
   * Takes a pile off the live inventory and schedules the durable removal - `CraftingService`'s shape.
   *
   * The in-memory half is synchronous so that the second of two players buying in the same tick is
   * checked against what the first has already spent.
   */
  private fun takeFromInventory(world: World, entityId: EntityId, itemId: Long, amount: Int): Boolean {
    if (amount == 0) return true

    val inventory = world.get(entityId, Inventory::class) ?: return false
    val masterId = world.get(entityId, Master::class)?.masterId ?: return false

    val held = inventory.getItems().filter { it.itemId == itemId && it.isStackable }.sumOf { it.amount }
    if (held < amount) return false

    inventory.removeAmount(itemId.toInt(), amount)
    asyncJobExecutor.submit(masterId) {
      inventoryService.consumeAll(masterId, listOf(itemId to amount))
    }

    return true
  }

  private fun itemIdOf(good: Commodity): Long? {
    return commodities.itemIdOf(good.id)
  }

  private fun codeFor(refusal: Shop.Refusal): OperationErrorProto.OpError = when (refusal) {
    Shop.Refusal.NOT_STOCKED -> OperationErrorProto.OpError.SHOP_NOT_STOCKED
    Shop.Refusal.OUT_OF_STOCK -> OperationErrorProto.OpError.SHOP_OUT_OF_STOCK
    Shop.Refusal.TREASURY_EMPTY -> OperationErrorProto.OpError.SHOP_TREASURY_EMPTY
    Shop.Refusal.TREASURY_FULL -> OperationErrorProto.OpError.SHOP_TREASURY_FULL
  }

  private fun deny(world: World, traderId: EntityId, code: OperationErrorProto.OpError) {
    val accountId = world.get(traderId, Account::class)?.accountId ?: return
    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * Cap on one trade, and the reason is arithmetic rather than balance: every unit is priced
     * separately against a town that has one fewer of them, so an unbounded amount is an unbounded
     * loop on the tick thread. Carrying capacity bounds a real trip well below this anyway.
     */
    const val MAX_UNITS_PER_TRADE = 500
  }
}
