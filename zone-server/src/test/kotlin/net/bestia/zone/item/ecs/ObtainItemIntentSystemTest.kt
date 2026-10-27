package net.bestia.zone.item.ecs

import java.time.Duration
import org.awaitility.Awaitility
import net.bestia.zone.item.persistence.ItemInstance
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.stat.ItemRoll
import net.bestia.zone.item.stat.ItemStat
import net.bestia.zone.item.stat.StatValue
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.CannotAcquireLockException
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.item.material.ItemMaterial

@ExtendWith(MockKExtension::class)
class ObtainItemIntentSystemTest {

  private val itemRepository = mockk<ItemRepository>()

  /** What the catalogue will find; read lazily, so a test stubs before its first tick. */
  private val catalogue = mutableListOf<Item>()
  private val lootItemEntitySpawner = mockk<LootItemEntitySpawner>(relaxed = true)
  private val inventoryService = mockk<InventoryService>(relaxed = true)

  // A real executor (single worker) rather than a mock: the async persist hand-off is exactly the
  // behavior under test, so it should actually run off-thread instead of being stubbed away.
  private val asyncJobExecutor = AsyncJobExecutor(workerCount = 1)

  private val sword = Item(id = 1L, identifier = "sword", weight = 120, type = Item.ItemType.ETC, material = ItemMaterial.METAL)
  private val boulder = Item(id = 2L, identifier = "boulder", weight = 5000, type = Item.ItemType.ETC, material = ItemMaterial.STONE)
  private val lantern = Item(id = 3L, identifier = "lantern", weight = 80, type = Item.ItemType.ETC, stackable = false, material = ItemMaterial.METAL)

  private lateinit var world: EcsWorld

  /** Hands the persist job's write-back to the test world, which only exists once the system is in it. */
  private val worldView = mockk<WorldView> {
    every { modify<Any?>(any(), any()) } answers { world.modify(firstArg(), secondArg()) }
  }

  private fun newSystem() = ObtainItemIntentSystem(
    itemTemplates = ItemTemplateRegistry(itemRepository),
    lootItemEntitySpawner = lootItemEntitySpawner,
    inventoryService = inventoryService,
    asyncJobExecutor = asyncJobExecutor,
    groundStackRemoval = GroundStackRemoval(PersistedEntityDeletionQueue()),
    itemStats = ItemStatCatalog(),
    worldView = worldView,
  )

  private fun setUp() {
    world = testWorld(systems = listOf(newSystem()))
    every { itemRepository.findAll() } answers { catalogue.toList() }
  }

  private fun stub(item: Item) {
    catalogue.add(item)
  }

  private fun verifyNoItemGranted() {
    verify(exactly = 0) { inventoryService.grantToOwner(any(), any(), any(), any(), any()) }
  }

  private fun verifyNoGroundDrop() {
    verify(exactly = 0) {
      lootItemEntitySpawner.spawnLootItem(any(), any(), any(), any(), any(), any())
    }
  }

  private fun createCarrier(capacityMax: Int, pos: Vec3L = Vec3L(0, 0, 0)): EntityId {
    return world.createEntity { id ->
      add(id, Inventory(mutableListOf()))
      add(id, CarryCapacity(current = 0, max = capacityMax))
      add(id, Account(ACCOUNT_ID))
      add(id, Master(MASTER_ID))
      add(id, Position.fromVec3(pos))
    }
  }

  @Test
  fun `create item intent adds the item to the ecs inventory and persists it asynchronously`() {
    setUp()
    stub(sword)
    val entity = createCarrier(capacityMax = 2475)

    world.modify(entity) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = sword.id, amount = 3)) }
    world.tick(0.1f)

    val granted = world.get(entity, Inventory::class)!!.getItem(sword.id.toInt())
    assertEquals(3, granted?.amount)
    assertFalse(world.has(entity, ObtainItemIntent.CreateItemIntent::class))

    verify(timeout = 1000) { inventoryService.grantToOwner(MASTER_ID, null, sword.id, 3, 0L) }
    verifyNoGroundDrop()
  }

  /** Each new copy can be named by the player in the tick it arrives, and its row is saved under that same id. */
  @Test
  fun `fresh unstackable items are held as one named copy per unit and saved under the same ids`() {
    setUp()
    stub(lantern)
    val entity = createCarrier(capacityMax = 2475)

    world.modify(entity) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = lantern.id, amount = 2)) }
    world.tick(0.1f)

    val copies = world.get(entity, Inventory::class)!!.getItems()
    assertEquals(listOf(1, 1), copies.map { it.amount })
    val heldIds = copies.map { it.uniqueId }
    assertTrue(heldIds.all { it != 0L })
    assertEquals(2, heldIds.toSet().size)

    verify(timeout = 1000) {
      inventoryService.grantToOwner(MASTER_ID, null, lantern.id, 2, 0L, null, null, heldIds)
    }
  }

  /** The live inventory already holds the item, so a grant that lost a lock race has to be written after all. */
  @Test
  fun `a grant that lost a lock race is written on the next try`() {
    setUp()
    stub(sword)
    every { inventoryService.grantToOwner(MASTER_ID, null, sword.id, 1, 0L) } throws
      CannotAcquireLockException("lock wait timeout") andThen null
    val entity = createCarrier(capacityMax = 2475)

    world.modify(entity) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = sword.id, amount = 1)) }
    world.tick(0.1f)
    asyncJobExecutor.awaitPending(MASTER_ID)

    verify(exactly = 2) { inventoryService.grantToOwner(MASTER_ID, null, sword.id, 1, 0L) }
    assertEquals(0L, asyncJobExecutor.failedJobs)
  }

  @Test
  fun `a grant that fails for good is counted as a failed job`() {
    setUp()
    stub(sword)
    every { inventoryService.grantToOwner(MASTER_ID, null, sword.id, 1, 0L) } throws IllegalStateException("broken")
    val entity = createCarrier(capacityMax = 2475)

    world.modify(entity) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = sword.id, amount = 1)) }
    world.tick(0.1f)
    asyncJobExecutor.awaitPending(MASTER_ID)

    verify(exactly = 1) { inventoryService.grantToOwner(MASTER_ID, null, sword.id, 1, 0L) }
    assertEquals(1L, asyncJobExecutor.failedJobs)
  }

  /** Its live bag is loaded from its own container, so a save into the master's would move the item on restart. */
  @Test
  fun `a bestia's item is persisted to its own container`() {
    setUp()
    stub(sword)
    val bestia = createCarrier(capacityMax = 2475)
    world.remove(bestia, Master::class)
    world.add(bestia, OwnedBestia(MASTER_ID, PLAYER_BESTIA_ID))

    world.modify(bestia) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = sword.id, amount = 3)) }
    world.tick(0.1f)

    verify(timeout = 1000) { inventoryService.grantToOwner(MASTER_ID, PLAYER_BESTIA_ID, sword.id, 3, 0L) }
  }

  @Test
  fun `create item intent drops the item on the ground instead when it would exceed carry capacity`() {
    setUp()
    stub(boulder)
    val entity = createCarrier(capacityMax = 50, pos = Vec3L(3, 4, 0))

    world.modify(entity) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = boulder.id, amount = 1)) }
    world.tick(0.1f)

    assertTrue(world.get(entity, Inventory::class)!!.isEmpty())
    verify {
      lootItemEntitySpawner.spawnLootItem(
        world = world, itemId = boulder.id, amount = 1, pos = Vec3L(3, 4, 0)
      )
    }
    verifyNoItemGranted()
  }

  /** A copy forged by a smith with a full bag is still the copy they forged, by them, once picked up. */
  @Test
  fun `a forged copy that overflows keeps its roll and its maker on the ground`() {
    setUp()
    stub(boulder)
    val entity = createCarrier(capacityMax = 50, pos = Vec3L(3, 4, 0))
    val roll = ItemRoll(stats = listOf(StatValue(ItemStat.STR, 3)), sockets = 1, itemLevel = 30)

    world.modify(entity) { id ->
      add(id, ObtainItemIntent.CreateItemIntent(itemId = boulder.id, amount = 1, roll = roll, provenance = ALICE))
    }
    world.tick(0.1f)

    verify {
      lootItemEntitySpawner.spawnLootItem(
        world = world, itemId = boulder.id, amount = 1, pos = Vec3L(3, 4, 0), provenance = ALICE, roll = roll
      )
    }
  }

  @Test
  fun `a picked up pile keeps its maker and roll`() {
    setUp()
    stub(sword)
    val roll = ItemRoll(stats = emptyList(), sockets = 0, itemLevel = 20)
    val looter = createCarrier(capacityMax = 2475, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, GroundItemStack(itemId = sword.id, amount = 2, provenance = ALICE, roll = roll))
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    assertEquals(ALICE, world.get(looter, Inventory::class)!!.getItem(sword.id.toInt())?.provenance)
    verify(timeout = 1000) { inventoryService.grantToOwner(MASTER_ID, null, sword.id, 2, 0L, roll, ALICE, emptyList()) }
  }

  @Test
  fun `create item intent drops the item on the ground when the entity has no inventory at all`() {
    setUp()
    stub(sword)
    val entity = world.createEntity { id ->
      add(id, Account(ACCOUNT_ID))
      add(id, Position.fromVec3(Vec3L(1, 2, 0)))
    }

    world.modify(entity) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = sword.id, amount = 1)) }
    world.tick(0.1f)

    verify {
      lootItemEntitySpawner.spawnLootItem(
        world = world, itemId = sword.id, amount = 1, pos = Vec3L(1, 2, 0)
      )
    }
  }

  @Test
  fun `create item intent for an unknown item is ignored without touching the inventory`() {
    setUp()
    val entity = createCarrier(capacityMax = 2475)

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
    val looter = createCarrier(capacityMax = 2475, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, GroundItemStack(itemId = sword.id, amount = 2))
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    // Broadcasting the vanish for the destroyed ground stack is ZoneEngine's job now (see
    // ZoneEngineTest), not this system's - it only needs to destroy the entity.
    assertFalse(world.isAlive(groundStack))
    assertEquals(2, world.get(looter, Inventory::class)!!.getItem(sword.id.toInt())?.amount)
    assertFalse(world.has(looter, ObtainItemIntent.LootItemIntent::class))

    verify(timeout = 1000) { inventoryService.grantToOwner(MASTER_ID, null, sword.id, 2, 0L) }
  }

  @Test
  fun `loot item intent re-attaches a unique instance carrying its unique id`() {
    setUp()
    stub(sword)
    val looter = createCarrier(capacityMax = 2475, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, GroundItemStack(itemId = sword.id, amount = 1, uniqueId = 77L))
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    val granted = world.get(looter, Inventory::class)!!.getItem(sword.id.toInt())
    assertEquals(77L, granted?.uniqueId)
    verify(timeout = 1000) { inventoryService.grantToOwner(MASTER_ID, null, sword.id, 1, 77L) }
  }

  @Test
  fun `a copy picked back up shows the wear its row has, not a fresh one`() {
    setUp()
    stub(sword)
    every { inventoryService.grantToOwner(MASTER_ID, null, sword.id, 1, 78L, any(), any(), any()) } returns
      ItemInstance(item = sword, maxDurability = 360, durability = 120, id = 78L)
    val looter = createCarrier(capacityMax = 2475, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, GroundItemStack(itemId = sword.id, amount = 1, uniqueId = 78L))
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted {
      val copy = world.get(looter, Inventory::class)!!.byPlayerItemId(78L)!!
      assertEquals(120, copy.durability)
      assertEquals(360, copy.maxDurability)
    }
  }

  /** Its destroy can wait for the end of the tick, and it must not end up in an inventory as well. */
  @Test
  fun `a stack that was destroyed this tick cannot be looted`() {
    setUp()
    stub(sword)
    val looter = createCarrier(capacityMax = 2475, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, GroundItemStack(itemId = sword.id, amount = 1))
      add(id, GroundItemIntegrity().also { it.destroyed = true })
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    assertTrue(world.get(looter, Inventory::class)!!.isEmpty())
    verifyNoItemGranted()
  }

  @Test
  fun `protected kill loot is refused to anyone but its killer`() {
    setUp()
    stub(sword)
    val looter = createCarrier(capacityMax = 2475, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, GroundItemStack(itemId = sword.id, amount = 1))
      add(id, LootProtection(ownerAccountId = ACCOUNT_ID + 1, remainingSeconds = 6f))
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    assertTrue(world.isAlive(groundStack))
    verifyNoItemGranted()
  }

  @Test
  fun `protected kill loot goes to its killer`() {
    setUp()
    stub(sword)
    val looter = createCarrier(capacityMax = 2475, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, GroundItemStack(itemId = sword.id, amount = 1))
      add(id, LootProtection(ownerAccountId = ACCOUNT_ID, remainingSeconds = 6f))
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    assertFalse(world.isAlive(groundStack))
    verify(timeout = 1000) { inventoryService.grantToOwner(MASTER_ID, null, sword.id, 1, 0L) }
  }

  @Test
  fun `loot item intent out of range leaves the ground stack untouched`() {
    setUp()
    val looter = createCarrier(capacityMax = 2475, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(100, 100, 0)))
      add(id, GroundItemStack(itemId = sword.id, amount = 2))
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
    val looter = createCarrier(capacityMax = 50, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, GroundItemStack(itemId = boulder.id, amount = 1))
    }

    world.modify(looter) { id -> add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack)) }
    world.tick(0.1f)

    assertTrue(world.isAlive(groundStack))
    assertTrue(world.get(looter, Inventory::class)!!.isEmpty())
    assertFalse(world.has(looter, ObtainItemIntent.LootItemIntent::class))
    verifyNoItemGranted()
  }

  /**
   * The gate used to read `CarryCapacity.current`, which nothing wrote after spawn - so it stayed at the
   * weight the entity logged in with and a session had no carry limit at all. The state below is what an
   * hour of looting produced: a full inventory behind a `current` that never moved.
   */
  @Test
  fun `the capacity gate reads the live inventory, not the last synced current`() {
    setUp()
    stub(sword)
    val entity = createCarrier(capacityMax = 500, pos = Vec3L(7, 8, 0))
    world.get(entity, Inventory::class)!!
      .addItem(Inventory.Item(itemId = boulder.id, amount = 1, weight = 450))

    world.modify(entity) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId = sword.id, amount = 1)) }
    world.tick(0.1f)

    assertNull(world.get(entity, Inventory::class)!!.getItem(sword.id.toInt()))
    verify {
      lootItemEntitySpawner.spawnLootItem(
        world = world, itemId = sword.id, amount = 1, pos = Vec3L(7, 8, 0)
      )
    }
    verifyNoItemGranted()
  }

  /**
   * One pass resolves both intents on the same entity. Gating each against a `current` that only refreshes
   * between ticks would let both through and overfill by a whole stack.
   */
  @Test
  fun `two grants in the same tick cannot both spend the same capacity`() {
    setUp()
    stub(sword)
    val looter = createCarrier(capacityMax = 500, pos = Vec3L(0, 0, 0))
    val groundStack = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, GroundItemStack(itemId = sword.id, amount = 1))
    }

    world.modify(looter) { id ->
      add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = groundStack))
      add(id, ObtainItemIntent.CreateItemIntent(itemId = sword.id, amount = 4))
    }
    world.tick(0.1f)

    // The loot fits (120 of 500) and the create does not (another 480 on top), so exactly one lands.
    assertEquals(1, world.get(looter, Inventory::class)!!.getItem(sword.id.toInt())?.amount)
    verify {
      lootItemEntitySpawner.spawnLootItem(
        world = world, itemId = sword.id, amount = 4, pos = Vec3L(0, 0, 0)
      )
    }
  }

  companion object {
    private const val ACCOUNT_ID = 100L
    private const val MASTER_ID = 55L
    private const val PLAYER_BESTIA_ID = 21L
    private val ALICE = ItemProvenance(crafterName = "Alice", worldName = "Genesis")
  }
}
