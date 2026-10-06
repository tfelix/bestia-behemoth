package net.bestia.zone.economy.shop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.economy.ShopTradeIntent
import net.bestia.zone.item.ecs.Inventory
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

/**
 * The live inventory and the database have to agree on what a trade took. The payout is the one thing that
 * cannot be taken back, so it waits for the durable take.
 */
class ShopTradeIntentSystemTest {

  private val bread = mockk<Commodity> { every { id } returns BREAD }
  private val shop = mockk<Shop>()
  private val inventoryService = mockk<InventoryService>()
  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val economy = mockk<SettlementEconomyService>(relaxed = true) {
    every { shopAt(any(), any()) } returns (SETTLEMENT to shop)
  }

  private val commodities = mockk<CommodityItems> {
    every { commodityOf(BREAD_ITEM) } returns bread
    every { itemIdOf(BREAD) } returns BREAD_ITEM
    every { coinItemId() } returns COIN_ITEM
  }

  private val inlineJobs = mockk<AsyncJobExecutor> {
    every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }

  private val world = testWorld(
    systems = listOf(
      ShopTradeIntentSystem(
        economy, commodities, inventoryService, inlineJobs, mockk(relaxed = true), out, mockk(relaxed = true)
      )
    )
  )

  @Test
  fun `a payment spread over two coin piles is taken from both`() {
    every { shop.quoteBuy(bread, 1) } returns Shop.Quote(units = 1, coins = 80)
    every { inventoryService.consumeAll(MASTER_ID, listOf(COIN_ITEM to 80)) } returns true
    val buyer = trader(Inventory.Item(COIN_ITEM, 50), Inventory.Item(COIN_ITEM, 50))

    trade(buyer, selling = false)

    assertEquals(20, held(buyer, COIN_ITEM))
    assertEquals(BREAD_ITEM, world.get(buyer, ObtainItemIntent.CreateItemIntent::class)?.itemId)
  }

  @Test
  fun `a payment the database refuses buys nothing and keeps the coins`() {
    every { shop.quoteBuy(bread, 1) } returns Shop.Quote(units = 1, coins = 80)
    every { inventoryService.consumeAll(MASTER_ID, any()) } returns false
    val buyer = trader(Inventory.Item(COIN_ITEM, 100))

    trade(buyer, selling = false)

    assertNull(world.get(buyer, ObtainItemIntent.CreateItemIntent::class), "nothing is paid out")
    assertEquals(100, held(buyer, COIN_ITEM))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.SHOP_CANNOT_AFFORD)) }
  }

  @Test
  fun `a price beyond the coin range is refused before anything is taken`() {
    every { shop.quoteSell(bread, 1) } returns Shop.Quote(units = 1, coins = Int.MAX_VALUE + 10L)
    every { inventoryService.consumeAll(MASTER_ID, any()) } returns true
    val seller = trader(Inventory.Item(BREAD_ITEM, 1))

    trade(seller, selling = true)

    assertNull(world.get(seller, ObtainItemIntent.CreateItemIntent::class))
    assertEquals(1, held(seller, BREAD_ITEM))
  }

  /** Opening the window checks the distance, but a hand-made packet can trade without ever opening it. */
  @Test
  fun `a merchant out of reach does not trade`() {
    every { shop.quoteSell(bread, 1) } returns Shop.Quote(units = 1, coins = 5)
    every { inventoryService.consumeAll(MASTER_ID, any()) } returns true
    val seller = trader(Inventory.Item(BREAD_ITEM, 1))

    trade(seller, selling = true, merchant = merchant(Position(500, 0, 0)))

    assertNull(world.get(seller, ObtainItemIntent.CreateItemIntent::class), "nothing is paid out")
    assertEquals(1, held(seller, BREAD_ITEM))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.SHOP_NONE_HERE)) }
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

  private fun trade(traderId: EntityId, selling: Boolean, merchant: EntityId = merchant(Position(1, 0, 0))) {
    world.add(traderId, ShopTradeIntent(BREAD_ITEM, 1, selling, setOf(BREAD), merchantEntityId = merchant))
    world.tick(0.05f)
  }

  private fun held(entityId: EntityId, itemId: Long): Int {
    return world.get(entityId, Inventory::class)!!.getItems().filter { it.itemId == itemId }.sumOf { it.amount }
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 2L
    const val SETTLEMENT = 7
    const val BREAD = "bread"
    const val BREAD_ITEM = 10L
    const val COIN_ITEM = 11L
  }
}
