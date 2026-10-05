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
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.data.repository.findByIdOrNull
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
 * What the trader gives is taken off the live inventory on the tick and then durably, and what they receive
 * is paid only once the durable take has committed. Paying first would mint goods or coin whenever the two
 * inventories disagree; a take the database refuses is given back instead. Only the *ledger* rides the
 * periodic save. So the worst a crash can cost is under a minute of one settlement's trades replayed as
 * though they had not happened - invisible, and self-correcting, because prices reconverge.
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
  private val itemRepository: ItemRepository,
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
    val counter = world.get(intent.merchantEntityId, Position::class)?.toVec3L()
    if (counter == null || !ShopReach.withinReach(position, counter)) {
      return deny(world, traderId, OperationErrorProto.OpError.SHOP_NONE_HERE)
    }
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

    // Prices are summed in Long, but coins are counted in Int.
    if (quote.coins > Int.MAX_VALUE) return deny(world, traderId, OperationErrorProto.OpError.SHOP_CANNOT_AFFORD)
    val coins = quote.coins.toInt()

    val moved = if (intent.selling) {
      sell(world, traderId, good, intent.amount, coins)
    } else {
      buy(world, traderId, good, intent.amount, coins)
    }
    if (!moved) return deny(world, traderId, OperationErrorProto.OpError.SHOP_CANNOT_AFFORD)

    economy.settle(settlement, good.id, intent.amount, quote.coins, intent.selling)
    offers.publish(world, traderId, intent.merchantEntityId, settlement, shop, intent.stocked)

    LOG.debug {
      "Entity $traderId ${if (intent.selling) "sold" else "bought"} ${intent.amount} ${good.id} " +
        "for ${quote.coins} in settlement $settlement"
    }
  }

  private fun buy(world: World, buyerId: EntityId, good: Commodity, units: Int, coins: Int): Boolean {
    val coinItem = commodities.coinItemId() ?: return false
    val goodsItem = itemIdOf(good) ?: return false

    return exchange(world, buyerId, give = coinItem to coins, receive = goodsItem to units)
  }

  private fun sell(world: World, sellerId: EntityId, good: Commodity, units: Int, coins: Int): Boolean {
    val goodsItem = itemIdOf(good) ?: return false
    val coinItem = commodities.coinItemId() ?: return false

    return exchange(world, sellerId, give = goodsItem to units, receive = coinItem to coins)
  }

  /**
   * Takes [give] off the live inventory now and pays [receive] once the durable take has committed.
   *
   * The in-memory half is synchronous so that the second of two players buying in the same tick is
   * checked against what the first has already spent.
   */
  private fun exchange(world: World, traderId: EntityId, give: Pair<Long, Int>, receive: Pair<Long, Int>): Boolean {
    val (giveItem, giveAmount) = give
    val inventory = world.get(traderId, Inventory::class) ?: return false
    val masterId = world.get(traderId, Master::class)?.masterId ?: return false
    val weight = inventory.getItems().firstOrNull { it.itemId == giveItem }?.weight ?: 0

    if (giveAmount > 0 && !inventory.removeFromStack(giveItem, giveAmount)) return false

    asyncJobExecutor.submit(masterId) {
      val taken = giveAmount == 0 || inventoryService.consumeAll(masterId, listOf(giveItem to giveAmount))

      if (taken) {
        pay(world, traderId, masterId, receive)
      } else {
        LOG.warn { "Master $masterId lost ${giveAmount}x $giveItem in the database before a shop trade; giving it back" }
        giveBack(world, traderId, Inventory.Item(giveItem, giveAmount, weight))
      }
    }

    return true
  }

  private fun pay(world: World, traderId: EntityId, masterId: Long, receive: Pair<Long, Int>) {
    val (itemId, amount) = receive
    if (amount == 0) return

    // One intent per entity: overwriting a pending grant would lose it.
    val queued = world.modify(traderId) { id ->
      if (has(id, ObtainItemIntent.CreateItemIntent::class)) {
        false
      } else {
        add(id, ObtainItemIntent.CreateItemIntent(itemId, amount))
        true
      }
    }

    if (queued != true) {
      // The live inventory catches up on the next login; the trade itself is already paid for.
      inventoryService.grantToMaster(masterId, itemId, amount)
    }
  }

  private fun giveBack(world: World, traderId: EntityId, taken: Inventory.Item) {
    val accountId = world.modify(traderId) { id ->
      get(id, Inventory::class)?.addItem(taken)
      get(id, Account::class)?.accountId
    } ?: return

    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OperationErrorProto.OpError.SHOP_CANNOT_AFFORD))
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
