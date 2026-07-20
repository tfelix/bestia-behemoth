package net.bestia.zone.ecs.item

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.loot.LootItemEntityFactory
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
class ObtainItemIntentSystemTest {

  private val itemRepository = mockk<ItemRepository>()
  private val lootItemEntityFactory = mockk<LootItemEntityFactory>(relaxed = true)
  private val inventoryService = mockk<InventoryService>(relaxed = true)
  private val connectionInfoService = mockk<ConnectionInfoService>()

  // A real executor (single worker) rather than a mock: the async persist hand-off is exactly the
  // behavior under test, so it should actually run off-thread instead of being stubbed away.
  private val asyncJobExecutor = AsyncJobExecutor(workerCount = 1)

  private val sword = Item(id = 1L, identifier = "sword", weight = 10, type = Item.ItemType.ETC)
  private val boulder = Item(id = 2L, identifier = "boulder", weight = 1000, type = Item.ItemType.ETC)

  private lateinit var world: World

  private fun newSystem() = ObtainItemIntentSystem(
    itemRepository = itemRepository,
    lootItemEntityFactory = lootItemEntityFactory,
    inventoryService = inventoryService,
    asyncJobExecutor = asyncJobExecutor,
    connectionInfoService = connectionInfoService,
  )

  private fun setUp() {
    world = testWorld(systems = listOf(newSystem()))
    every { connectionInfoService.getMasterId(any()) } returns MASTER_ID
  }

  private fun stub(item: Item) {
    every { itemRepository.findById(item.id) } returns Optional.of(item)
  }

  private fun verifyNoItemGranted() {
    verify(exactly = 0) { inventoryService.grantToMaster(any(), any(), any(), any()) }
  }

  private fun verifyNoGroundDrop() {
    verify(exactly = 0) {
      lootItemEntityFactory.createLootEntity(any(), any(), any(), any(), any(), any())
    }
  }

  private fun createCarrier(capacityMax: Int, pos: Vec3L = Vec3L(0, 0, 0)): EntityId {
    return world.createEntity { id ->
      add(id, Inventory(mutableListOf()))
      add(id, CarryCapacity(current = 0, max = capacityMax))
      add(id, Account(ACCOUNT_ID))
      add(id, Position.fromVec3(pos))
    }
  }

  @Test
  fun `create item intent adds the item to the ecs inventory and persists it asynchronously`() {
    setUp()
    stub(sword)
    val entity = createCarrier(capacityMax = 100)

    world.modify(entity) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = sword.id, amount = 3)) }
    world.tick(0.1f)

    val granted = world.get(entity, Inventory::class)!!.getItem(sword.id.toInt())
    assertEquals(3, granted?.amount)
    assertFalse(world.has(entity, ObtainItemIntent.CreateItemIntent::class))

    verify(timeout = 1000) { inventoryService.grantToMaster(MASTER_ID, sword, 3, 0L) }
    verifyNoGroundDrop()
  }

  @Test
  fun `create item intent drops the item on the ground instead when it would exceed carry capacity`() {
    setUp()
    stub(boulder)
    val entity = createCarrier(capacityMax = 5, pos = Vec3L(3, 4, 0))

    world.modify(entity) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = boulder.id, amount = 1)) }
    world.tick(0.1f)

    assertTrue(world.get(entity, Inventory::class)!!.isEmpty())
    verify {
      lootItemEntityFactory.createLootEntity(
        world = world, itemId = boulder.id, amount = 1, pos = Vec3L(3, 4, 0)
      )
    }
    verifyNoItemGranted()
  }

  @Test
  fun `create item intent drops the item on the ground when the entity has no inventory at all`() {
    setUp()
    stub(sword)
    // Mirrors a player bestia entity today: no Inventory/CarryCapacity component.
    val entity = world.createEntity { id ->
      add(id, Account(ACCOUNT_ID))
      add(id, Position.fromVec3(Vec3L(1, 2, 0)))
    }

    world.modify(entity) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = sword.id, amount = 1)) }
    world.tick(0.1f)

    verify {
      lootItemEntityFactory.createLootEntity(
        world = world, itemId = sword.id, amount = 1, pos = Vec3L(1, 2, 0)
      )
    }
  }

  @Test
  fun `create item intent for an unknown item is ignored without touching the inventory`() {
    setUp()
    every { itemRepository.findById(999L) } returns Optional.empty()
    val entity = createCarrier(capacityMax = 100)

    world.modify(entity) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = 999L, amount = 1)) }
    world.tick(0.1f)

    assertTrue(world.get(entity, Inventory::class)!!.isEmpty())
    assertFalse(world.has(entity, ObtainItemIntent.CreateItemIntent::class))
    verifyNoGroundDrop()
    verifyNoItemGranted()
  }

  @Test
  fun `loot item intent within range grants the item and destroys the ground stack`() {
    setUp()
    stub(sword)
    val looter = createCarrier(capacityMax = 100, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, ItemVisual(itemId = sword.id, amount = 2))
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    // Broadcasting the vanish for the destroyed ground stack is ZoneEngine's job now (see
    // ZoneEngineTest), not this system's - it only needs to destroy the entity.
    assertFalse(world.isAlive(groundStack))
    assertEquals(2, world.get(looter, Inventory::class)!!.getItem(sword.id.toInt())?.amount)
    assertFalse(world.has(looter, ObtainItemIntent.LootItemIntent::class))

    verify(timeout = 1000) { inventoryService.grantToMaster(MASTER_ID, sword, 2, 0L) }
  }

  @Test
  fun `loot item intent re-attaches a unique instance carrying its unique id`() {
    setUp()
    stub(sword)
    val looter = createCarrier(capacityMax = 100, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, ItemVisual(itemId = sword.id, amount = 1, uniqueId = 77L))
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    val granted = world.get(looter, Inventory::class)!!.getItem(sword.id.toInt())
    assertEquals(77L, granted?.uniqueId)
    verify(timeout = 1000) { inventoryService.grantToMaster(MASTER_ID, sword, 1, 77L) }
  }

  @Test
  fun `loot item intent out of range leaves the ground stack untouched`() {
    setUp()
    val looter = createCarrier(capacityMax = 100, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(100, 100, 0)))
      add(id, ItemVisual(itemId = sword.id, amount = 2))
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    assertTrue(world.isAlive(groundStack))
    assertTrue(world.get(looter, Inventory::class)!!.isEmpty())
    verifyNoItemGranted()
  }

  @Test
  fun `loot item intent over capacity leaves the ground stack untouched instead of losing it`() {
    setUp()
    stub(boulder)
    val looter = createCarrier(capacityMax = 5, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, ItemVisual(itemId = boulder.id, amount = 1))
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    assertTrue(world.isAlive(groundStack))
    assertTrue(world.get(looter, Inventory::class)!!.isEmpty())
    assertFalse(world.has(looter, ObtainItemIntent.LootItemIntent::class))
    verifyNoItemGranted()
  }

  companion object {
    private const val ACCOUNT_ID = 100L
    private const val MASTER_ID = 55L
  }
}
