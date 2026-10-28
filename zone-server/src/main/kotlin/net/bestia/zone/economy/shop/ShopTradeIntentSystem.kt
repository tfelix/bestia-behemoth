package net.bestia.zone.economy.shop

import net.bestia.zone.entity.GameMetrics
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.economy.ecs.ShopTradeIntent
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.CarryCapacitySystem
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent
import kotlin.math.abs
import net.bestia.zone.prop.CollectPropIntentSystem

/**
 * Resolves a [ShopTradeIntent]: quotes the basket against the town the player is standing in, moves the
 * goods both ways and the balance in coin, and sends the window back so the new prices are visible.
 *
 * ### In the items phase, before death
 *
 * Immediately before `CollectPropIntentSystem`, which it mirrors, and for that system's reasons: before
 * `DeathSystem` and `PersistAndRemoveSystem` (later phases), because `World.addNow` requires its
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
class ShopTradeIntentSystem(
  private val economy: SettlementEconomyService,
  private val commodities: CommodityItems,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val offers: ShopOfferPublisher,
  private val outMessageProcessor: OutMessageProcessor,
  private val itemTemplates: ItemTemplateRegistry,
  private val metrics: GameMetrics = GameMetrics(),
) : System {
  override val phase = Phase.ITEMS
  override val after = setOf(CarryCapacitySystem::class)
  override val before = setOf(CollectPropIntentSystem::class)

  override val reads: ComponentClassSet = setOf(Account::class, Master::class, Position::class, CarryCapacity::class)

  /**
   * [ObtainItemIntent.CreateItemsIntent] is declared although it lands on the same entity this queries -
   * it is what keeps this out of `ObtainItemIntentSystem`'s scheduler wave. `Inventory` is written
   * directly, so the second player of a pair sees the first player's coin already gone.
   */
  override val writes: ComponentClassSet = setOf(
    ShopTradeIntent::class, ObtainItemIntent.CreateItemsIntent::class, Inventory::class
  )

  override fun update(world: World, deltaTime: Float) {
    world.query(ShopTradeIntent::class).each { traderId ->
      val intent = get<ShopTradeIntent>()
      trade(world, traderId, intent)
      world.remove(traderId, ShopTradeIntent::class)
    }
  }

  private fun trade(world: World, traderId: EntityId, intent: ShopTradeIntent) {
    if (!ShopTradeLine.isWellFormed(intent.lines)) return

    val position = world.get(traderId, Position::class)?.toVec3L()
      ?: return deny(world, traderId, OperationErrorProto.OpError.SHOP_NONE_HERE)
    val counter = world.get(intent.counter.merchantId, Position::class)?.toVec3L()
    if (counter == null || !ShopReach.withinReach(position, counter)) {
      return deny(world, traderId, OperationErrorProto.OpError.SHOP_NONE_HERE)
    }
    val (settlement, shop) = economy.shopAt(position.x, position.y)
      ?: return deny(world, traderId, OperationErrorProto.OpError.SHOP_NONE_HERE)

    val basket = ShopTradeLine.basketOf(intent.lines, intent.counter, commodities)
    val quote = shop.quoteBasket(basket)

    quote.refusal?.let {
      metrics.shopRefused(it.name)
      return deny(world, traderId, ShopCounter.codeFor(it))
    }

    if (quote.balance < intent.expectedBalance) {
      metrics.shopRefused("PRICE_CHANGED")
      deny(world, traderId, OperationErrorProto.OpError.SHOP_PRICE_CHANGED)
      return offers.publish(world, traderId, settlement, shop, intent.counter)
    }

    // Prices are summed in Long, but coins are counted in Int.
    if (abs(quote.balance) > Int.MAX_VALUE) {
      metrics.shopRefused("CANNOT_AFFORD")
      return deny(world, traderId, OperationErrorProto.OpError.SHOP_CANNOT_AFFORD)
    }

    if (!canCarry(world, traderId, intent.lines)) {
      metrics.shopRefused("TOO_HEAVY")
      return deny(world, traderId, OperationErrorProto.OpError.SHOP_TOO_HEAVY)
    }

    if (!exchange(world, traderId, givenFor(intent.lines, quote.balance), receivedFor(intent.lines, quote.balance))) {
      metrics.shopRefused("CANNOT_AFFORD")
      return deny(world, traderId, OperationErrorProto.OpError.SHOP_CANNOT_AFFORD)
    }

    basket.forEachIndexed { index, line ->
      // Every commodity is known here: a line naming none was refused as NOT_STOCKED above.
      val good = line.commodity ?: return@forEachIndexed
      economy.settle(settlement, good.id, line.units, quote.lineCoins[index], line.selling)
      metrics.shopTrade(line.selling, line.units, quote.lineCoins[index])
    }
    offers.publish(world, traderId, settlement, shop, intent.counter)

    LOG.debug { "Entity $traderId traded ${intent.lines} for a balance of ${quote.balance} in settlement $settlement" }
  }

  /** The goods handed over, and the coin when the player pays the difference. */
  private fun givenFor(lines: List<ShopTradeLine>, balance: Long): List<Pair<Long, Int>> {
    return lines.filter { it.selling }.map { it.itemId to it.amount } + coinsOf(-balance)
  }

  /** The goods taken, and the coin when the town pays the difference. */
  private fun receivedFor(lines: List<ShopTradeLine>, balance: Long): List<Pair<Long, Int>> {
    return lines.filterNot { it.selling }.map { it.itemId to it.amount } + coinsOf(balance)
  }

  private fun coinsOf(amount: Long): List<Pair<Long, Int>> {
    val coin = commodities.coinItemId() ?: return emptyList()

    return if (amount > 0) listOf(coin to amount.toInt()) else emptyList()
  }

  /**
   * Weighed against the live [Inventory], as `ObtainItemIntentSystem` does. A basket that leaves the trader
   * lighter always fits, so an over-laden player can still sell their way back under the limit.
   */
  private fun canCarry(world: World, traderId: EntityId, lines: List<ShopTradeLine>): Boolean {
    val inventory = world.get(traderId, Inventory::class) ?: return false
    val capacity = world.get(traderId, CarryCapacity::class) ?: return true

    val change = lines.sumOf { line ->
      val weight = (itemTemplates.templateOf(line.itemId)?.weight ?: 0) * line.amount
      if (line.selling) -weight else weight
    }

    return change <= 0 || inventory.totalWeight + change <= capacity.max
  }

  /**
   * Takes [give] off the live inventory now and pays [receive] once the durable take has committed.
   *
   * The in-memory half is synchronous so that the second of two players buying in the same tick is
   * checked against what the first has already spent.
   */
  private fun exchange(
    world: World,
    traderId: EntityId,
    give: List<Pair<Long, Int>>,
    receive: List<Pair<Long, Int>>,
  ): Boolean {
    val inventory = world.get(traderId, Inventory::class) ?: return false
    val masterId = world.get(traderId, Master::class)?.masterId ?: return false

    val taken = ArrayList<Inventory.Item>()
    for ((itemId, amount) in give) {
      val piles = inventory.takeAcrossPiles(itemId, amount)
      if (piles == null) {
        inventory.addItems(taken)
        return false
      }
      taken += piles
    }

    asyncJobExecutor.submit(masterId) {
      val durablyTaken = give.isEmpty() || inventoryService.consumeAll(masterId, give)

      if (durablyTaken) {
        pay(world, traderId, masterId, receive)
      } else {
        LOG.warn { "Master $masterId lost $give in the database before a shop trade; giving it back" }
        giveBack(world, traderId, taken)
      }
    }

    return true
  }

  private fun pay(world: World, traderId: EntityId, masterId: Long, receive: List<Pair<Long, Int>>) {
    val grants = receive.map { (itemId, amount) -> ObtainItemIntent.CreateItemIntent(itemId, amount) }
    if (grants.isEmpty()) return

    val queued = world.modify(traderId) { id ->
      val pending = get(id, ObtainItemIntent.CreateItemsIntent::class)?.grants.orEmpty()
      add(id, ObtainItemIntent.CreateItemsIntent(pending + grants))
    }

    if (queued == null) {
      // The live inventory catches up on the next login; the trade itself is already paid for.
      grants.forEach { inventoryService.grantToMaster(masterId, it.itemId, it.amount) }
    }
  }

  private fun giveBack(world: World, traderId: EntityId, taken: List<Inventory.Item>) {
    val accountId = world.modify(traderId) { id ->
      get(id, Inventory::class)?.addItems(taken)
      get(id, Account::class)?.accountId
    } ?: return

    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OperationErrorProto.OpError.SHOP_CANNOT_AFFORD))
  }

  private fun deny(world: World, traderId: EntityId, code: OperationErrorProto.OpError) {
    val accountId = world.get(traderId, Account::class)?.accountId ?: return
    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
