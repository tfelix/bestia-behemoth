package net.bestia.zone.economy.shop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.economy.ecs.ShopTradeIntent
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.economy.Commodity
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.Shop
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live inventory and the database have to agree on what a trade took. The payout is the one thing that
 * cannot be taken back, so it waits for the durable take.
 */
class ShopTradeIntentSystemTest {

  private val bread = mockk<Commodity> { every { id } returns BREAD }
  private val grain = mockk<Commodity> { every { id } returns GRAIN }
  private val shop = mockk<Shop>()
  private val inventoryService = mockk<InventoryService>()
  private val offers = mockk<ShopOfferPublisher>(relaxed = true)
  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val economy = mockk<SettlementEconomyService>(relaxed = true) {
    every { shopAt(any(), any()) } returns (SETTLEMENT to shop)
  }

  private val commodities = mockk<CommodityItems> {
    every { commodityOf(BREAD_ITEM) } returns bread
    every { commodityOf(GRAIN_ITEM) } returns grain
    every { itemIdOf(BREAD) } returns BREAD_ITEM
    every { coinItemId() } returns COIN_ITEM
  }

  private val itemTemplates = mockk<ItemTemplateRegistry> {
    every { templateOf(BREAD_ITEM) } returns template(BREAD_ITEM, weight = 5)
    every { templateOf(GRAIN_ITEM) } returns template(GRAIN_ITEM, weight = 2)
  }

  private val inlineJobs = mockk<AsyncJobExecutor> {
    every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }

  private val world = testWorld(
    systems = listOf(
      ShopTradeIntentSystem(economy, commodities, inventoryService, inlineJobs, offers, out, itemTemplates)
    )
  )

  @Test
  fun `a payment spread over two coin piles is taken from both`() {
    quote(balance = -80, 80)
    every { inventoryService.consumeAll(MASTER_ID, listOf(COIN_ITEM to 80)) } returns true
    val buyer = trader(Inventory.Item(COIN_ITEM, 50), Inventory.Item(COIN_ITEM, 50))

    trade(buyer, listOf(BUY_BREAD), expectedBalance = -80)

    assertEquals(20, held(buyer, COIN_ITEM))
    assertEquals(listOf(BREAD_ITEM to 1), granted(buyer))
  }

  @Test
  fun `selling pays for buying, and only the difference changes hands`() {
    quote(balance = -50, 80, 30)
    every { inventoryService.consumeAll(MASTER_ID, listOf(GRAIN_ITEM to 3, COIN_ITEM to 50)) } returns true
    val trader = trader(Inventory.Item(COIN_ITEM, 100), Inventory.Item(GRAIN_ITEM, 3))

    trade(trader, listOf(BUY_BREAD, SELL_GRAIN), expectedBalance = -50)

    assertEquals(50, held(trader, COIN_ITEM))
    assertEquals(0, held(trader, GRAIN_ITEM))
    assertEquals(listOf(BREAD_ITEM to 1), granted(trader))
  }

  @Test
  fun `a basket worth more sold than bought pays the trader the difference`() {
    quote(balance = 20, 10, 30)
    every { inventoryService.consumeAll(MASTER_ID, listOf(GRAIN_ITEM to 3)) } returns true
    val trader = trader(Inventory.Item(GRAIN_ITEM, 3))

    trade(trader, listOf(BUY_BREAD, SELL_GRAIN), expectedBalance = 20)

    assertEquals(listOf(BREAD_ITEM to 1, COIN_ITEM to 20), granted(trader))
  }

  @Test
  fun `a grant still pending is added to, not replaced`() {
    quote(balance = -10, 10)
    every { inventoryService.consumeAll(MASTER_ID, any()) } returns true
    val buyer = trader(Inventory.Item(COIN_ITEM, 100))
    world.add(buyer, ObtainItemIntent.CreateItemsIntent(listOf(ObtainItemIntent.CreateItemIntent(GRAIN_ITEM, 4))))

    trade(buyer, listOf(BUY_BREAD), expectedBalance = -10)

    assertEquals(listOf(GRAIN_ITEM to 4, BREAD_ITEM to 1), granted(buyer))
  }

  @Test
  fun `a payment the database refuses buys nothing and keeps the coins`() {
    quote(balance = -80, 80)
    every { inventoryService.consumeAll(MASTER_ID, any()) } returns false
    val buyer = trader(Inventory.Item(COIN_ITEM, 100))

    trade(buyer, listOf(BUY_BREAD), expectedBalance = -80)

    assertNull(world.get(buyer, ObtainItemIntent.CreateItemsIntent::class), "nothing is paid out")
    assertEquals(100, held(buyer, COIN_ITEM))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.SHOP_CANNOT_AFFORD)) }
  }

  @Test
  fun `goods already taken go back when the coin for the rest is short`() {
    quote(balance = -50, 80, 30)
    val trader = trader(Inventory.Item(COIN_ITEM, 10), Inventory.Item(GRAIN_ITEM, 3))

    trade(trader, listOf(BUY_BREAD, SELL_GRAIN), expectedBalance = -50)

    assertEquals(3, held(trader, GRAIN_ITEM))
    assertEquals(10, held(trader, COIN_ITEM))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.SHOP_CANNOT_AFFORD)) }
  }

  @Test
  fun `a price that moved against the trader since the quote is refused`() {
    quote(balance = -80, 80)
    val buyer = trader(Inventory.Item(COIN_ITEM, 100))

    trade(buyer, listOf(BUY_BREAD), expectedBalance = -70)

    assertEquals(100, held(buyer, COIN_ITEM))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.SHOP_PRICE_CHANGED)) }
    verify { offers.publish(any(), buyer, SETTLEMENT, shop, any()) }
  }

  @Test
  fun `a basket too heavy to carry is refused`() {
    quote(balance = -30, 30)
    val buyer = trader(Inventory.Item(COIN_ITEM, 100))
    world.add(buyer, CarryCapacity(current = 0, max = 10))

    trade(buyer, listOf(ShopTradeLine(BREAD_ITEM, 3, selling = false)), expectedBalance = -30)

    assertEquals(100, held(buyer, COIN_ITEM))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.SHOP_TOO_HEAVY)) }
  }

  @Test
  fun `a basket that lightens the load fits even an overloaded trader`() {
    quote(balance = 25, 5, 30)
    every { inventoryService.consumeAll(MASTER_ID, any()) } returns true
    val trader = trader(Inventory.Item(GRAIN_ITEM, 3, weight = 2), Inventory.Item(COIN_ITEM, 1, weight = 20))
    world.add(trader, CarryCapacity(current = 0, max = 10))

    trade(trader, listOf(BUY_BREAD, SELL_GRAIN), expectedBalance = 25)

    assertTrue(granted(trader).isNotEmpty())
  }

  @Test
  fun `a balance beyond the coin range is refused before anything is taken`() {
    quote(balance = Int.MAX_VALUE + 10L, Int.MAX_VALUE + 10L)
    val seller = trader(Inventory.Item(GRAIN_ITEM, 3))

    trade(seller, listOf(SELL_GRAIN), expectedBalance = 0)

    assertNull(world.get(seller, ObtainItemIntent.CreateItemsIntent::class))
    assertEquals(3, held(seller, GRAIN_ITEM))
  }

  @Test
  fun `the same item on both sides is dropped unanswered`() {
    val trader = trader(Inventory.Item(BREAD_ITEM, 1), Inventory.Item(COIN_ITEM, 100))

    trade(trader, listOf(BUY_BREAD, ShopTradeLine(BREAD_ITEM, 1, selling = true)), expectedBalance = 0)

    verify(exactly = 0) { shop.quoteBasket(any()) }
  }

  /** Opening the window checks the distance, but a hand-made packet can trade without ever opening it. */
  @Test
  fun `a merchant out of reach does not trade`() {
    quote(balance = 30, 30)
    val seller = trader(Inventory.Item(GRAIN_ITEM, 3))

    trade(seller, listOf(SELL_GRAIN), expectedBalance = 30, merchant = merchant(Position(500, 0, 0)))

    assertNull(world.get(seller, ObtainItemIntent.CreateItemsIntent::class), "nothing is paid out")
    assertEquals(3, held(seller, GRAIN_ITEM))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.SHOP_NONE_HERE)) }
  }

  private fun quote(balance: Long, vararg lineCoins: Long) {
    every { shop.quoteBasket(any()) } returns Shop.BasketQuote(lineCoins.toList(), balance)
  }

  private fun trader(vararg held: Inventory.Item): EntityId {
    return world.createEntity { id ->
      add(id, Position(0, 0, 0))
      add(id, Account(ACCOUNT_ID))
      add(id, Master(MASTER_ID))
      add(id, Inventory(held.toMutableList()))
    }
  }

  private fun merchant(at: Position): EntityId {
    return world.createEntity { id -> add(id, at) }
  }

  private fun trade(
    traderId: EntityId,
    lines: List<ShopTradeLine>,
    expectedBalance: Long,
    merchant: EntityId = merchant(Position(1, 0, 0)),
  ) {
    val counter = Counter(merchant, "Mira", setOf(BREAD, GRAIN), ShopHours.ALWAYS_OPEN)
    world.add(traderId, ShopTradeIntent(lines, expectedBalance, counter))
    world.tick(0.05f)
  }

  private fun held(entityId: EntityId, itemId: Long): Int {
    return world.get(entityId, Inventory::class)!!.getItems().filter { it.itemId == itemId }.sumOf { it.amount }
  }

  private fun granted(entityId: EntityId): List<Pair<Long, Int>> {
    val grants = world.get(entityId, ObtainItemIntent.CreateItemsIntent::class)?.grants.orEmpty()

    return grants.map { it.itemId to it.amount }
  }

  private fun template(itemId: Long, weight: Int): ItemTemplateRegistry.Template {
    return mockk { every { this@mockk.weight } returns weight; every { id } returns itemId }
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 2L
    const val SETTLEMENT = 7
    const val BREAD = "bread"
    const val GRAIN = "grain"
    const val BREAD_ITEM = 10L
    const val GRAIN_ITEM = 12L
    const val COIN_ITEM = 11L

    val BUY_BREAD = ShopTradeLine(BREAD_ITEM, 1, selling = false)
    val SELL_GRAIN = ShopTradeLine(GRAIN_ITEM, 3, selling = true)
  }
}
