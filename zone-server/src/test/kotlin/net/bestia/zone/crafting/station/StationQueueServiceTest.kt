package net.bestia.zone.crafting.station

import net.bestia.zone.prop.station.QueueEntry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.crafting.Crafters
import net.bestia.zone.crafting.station.StationFixture.Companion.CHEST
import net.bestia.zone.crafting.station.StationFixture.Companion.OWNER
import net.bestia.zone.crafting.station.StationFixture.Companion.PLANK
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.crafting.station.StationFixture.Companion.RECIPE
import net.bestia.zone.crafting.station.StationFixture.Companion.STATION
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StationQueueServiceTest {

  private val fixture = StationFixture()
  private val inventoryService = mockk<InventoryService>().also {
    every { it.consumeAllFrom(any(), any()) } returns true
    every { it.grantStacksTo(any(), any(), any()) } returns true
  }
  private val profiles = mockk<CrafterProfiles>()
  private val lootItems = mockk<LootItemEntitySpawner>(relaxed = true)
  private val service = StationQueueService(
    structures = fixture.structures,
    recipes = fixture.recipes,
    crafting = mockk<CraftingService> { every { refusalToQueue(any(), any()) } returns null },
    crafters = fixture.crafters,
    profiles = profiles,
    workers = fixture.workers,
    chests = fixture.chests,
    itemTemplates = fixture.itemTemplates,
    inventoryService = inventoryService,
    asyncJobExecutor = mockk<AsyncJobExecutor> {
      every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
    },
    lootItems = lootItems,
    work = StationWork(),
    states = mockk(relaxed = true),
  )

  @Test
  fun `queuing pays the ingredients for the whole amount into the station`() {
    val owner = fixture.givenOwner(planks = 10)

    assertNull(service.enqueue(fixture.world, owner, STATION, RECIPE, amount = 3))

    assertEquals(listOf(QueueEntry(RECIPE, 3)), fixture.station.queue)
    assertEquals(mapOf(PLANK to 6), fixture.station.held)
    assertEquals(4, fixture.planksIn(owner))
    verify { inventoryService.consumeAllFrom(OWNER, mapOf(ItemHolder.Master to mapOf(PLANK to 6))) }
  }

  @Test
  fun `what the bag lacks is paid from the chest next to the station`() {
    val owner = fixture.givenOwner(planks = 4)
    fixture.givenChest(planks = 5)

    assertNull(service.enqueue(fixture.world, owner, STATION, RECIPE, amount = 3))

    assertEquals(mapOf(PLANK to 6), fixture.station.held)
    assertEquals(0, fixture.planksIn(owner))
    assertEquals(3, fixture.planksIn(fixture.chestContents))
    verify {
      inventoryService.consumeAllFrom(
        OWNER, mapOf(ItemHolder.Master to mapOf(PLANK to 4), ItemHolder.Chest(CHEST) to mapOf(PLANK to 2))
      )
    }
  }

  @Test
  fun `bag and chest together too short queue nothing and take nothing`() {
    val owner = fixture.givenOwner(planks = 4)
    fixture.givenChest(planks = 1)

    assertEquals(OpError.CRAFT_MISSING_MATERIALS, service.enqueue(fixture.world, owner, STATION, RECIPE, amount = 3))

    assertTrue(fixture.station.queue.isEmpty())
    assertEquals(4, fixture.planksIn(owner))
    assertEquals(1, fixture.planksIn(fixture.chestContents))
  }

  @Test
  fun `a payment the database refuses goes back to the bag and the chest`() {
    val owner = fixture.givenOwner(planks = 4)
    fixture.givenChest(planks = 5)
    every { inventoryService.consumeAllFrom(any(), any()) } returns false

    service.enqueue(fixture.world, owner, STATION, RECIPE, amount = 3)
    fixture.world.tick(0f)

    assertTrue(fixture.station.queue.isEmpty())
    assertTrue(fixture.station.held.isEmpty())
    assertEquals(4, fixture.planksIn(owner))
    assertEquals(5, fixture.planksIn(fixture.chestContents))
  }

  @Test
  fun `the owner queues from afar paying only from the chest`() {
    fixture.givenOwner(planks = 10, at = Vec3L(500, 0, 0))
    fixture.givenChest(planks = 5)
    every { profiles.knowledgeOf(OWNER) } returns crafterKnowingEverything()

    assertNull(service.enqueueFromChest(fixture.world, OWNER, STATION, RECIPE, amount = 2))
    assertEquals(
      OpError.CRAFT_MISSING_MATERIALS, service.enqueueFromChest(fixture.world, OWNER, STATION, RECIPE, amount = 1)
    )

    assertEquals(listOf(QueueEntry(RECIPE, 2)), fixture.station.queue)
    assertEquals(1, fixture.planksIn(fixture.chestContents))
    verify { inventoryService.consumeAllFrom(OWNER, mapOf(ItemHolder.Chest(CHEST) to mapOf(PLANK to 4))) }
  }

  @Test
  fun `queuing from afar needs a chest next to the station and the station's owner`() {
    every { profiles.knowledgeOf(any()) } returns crafterKnowingEverything()

    assertEquals(OpError.CRAFT_MISSING_MATERIALS, service.enqueueFromChest(fixture.world, OWNER, STATION, RECIPE, 1))
    fixture.givenChest(planks = 5)
    assertEquals(OpError.CRAFT_NOT_POSSIBLE, service.enqueueFromChest(fixture.world, 99L, STATION, RECIPE, 1))
  }

  @Test
  fun `too few ingredients for the amount queues nothing and takes nothing`() {
    val owner = fixture.givenOwner(planks = 5)

    assertEquals(OpError.CRAFT_MISSING_MATERIALS, service.enqueue(fixture.world, owner, STATION, RECIPE, amount = 3))

    assertTrue(fixture.station.queue.isEmpty())
    assertEquals(5, fixture.planksIn(owner))
  }

  @Test
  fun `only the owner may queue, and only from next to the station`() {
    val stranger = fixture.givenOwner(planks = 10, masterId = 99L)
    val farAway = fixture.givenOwner(planks = 10, at = Vec3L(20, 0, 0))

    assertEquals(OpError.CRAFT_NOT_POSSIBLE, service.enqueue(fixture.world, stranger, STATION, RECIPE, amount = 1))
    assertEquals(OpError.CRAFT_NOT_POSSIBLE, service.enqueue(fixture.world, farAway, STATION, RECIPE, amount = 1))
  }

  @Test
  fun `a full queue takes no more entries`() {
    val owner = fixture.givenOwner(planks = 100)
    repeat(StationQueueService.MAX_ENTRIES) { service.enqueue(fixture.world, owner, STATION, RECIPE, amount = 1) }

    assertEquals(OpError.CRAFT_ALREADY_IN_PROGRESS, service.enqueue(fixture.world, owner, STATION, RECIPE, amount = 1))
  }

  private fun crafterKnowingEverything(): Crafters.Crafter {
    return fixture.crafters.Crafter(Master(OWNER, "Joiner"), null, null, null)
  }

  @Test
  fun `cancelling gives back the unused ingredients`() {
    val owner = fixture.givenOwner(planks = 0)
    fixture.station = fixture.station.copy(queue = listOf(QueueEntry(RECIPE, 3, done = 1)), held = mapOf(PLANK to 4))

    assertNull(service.cancel(fixture.world, owner, STATION, index = 0))

    val refund = fixture.world.get(owner, ObtainItemIntent.CreateItemsIntent::class)!!.grants.single()
    assertEquals(PLANK to 4, refund.itemId to refund.amount)
    assertTrue(fixture.station.queue.isEmpty())
    assertTrue(fixture.station.held.isEmpty())
  }

  @Test
  fun `what does not fit in the bag stays in the station`() {
    // Planks weigh 10 each, so a bag that carries 20 takes two of the four.
    val owner = fixture.givenOwner(planks = 0, maxWeight = 20)
    fixture.station = fixture.station.copy(queue = listOf(QueueEntry(RECIPE, 2)), held = mapOf(PLANK to 4))

    service.cancel(fixture.world, owner, STATION, index = 0)

    val refund = fixture.world.get(owner, ObtainItemIntent.CreateItemsIntent::class)!!.grants.single()
    assertEquals(2, refund.amount)
    assertEquals(mapOf(PLANK to 2), fixture.station.held)
  }

  @Test
  fun `cancelling from afar puts the unused ingredients into the chest beside the station`() {
    fixture.givenChest(planks = 1)
    fixture.station = fixture.station.copy(queue = listOf(QueueEntry(RECIPE, 3, done = 1)), held = mapOf(PLANK to 4))

    assertNull(service.cancelFromAfar(fixture.world, OWNER, STATION, index = 0))

    assertEquals(5, fixture.planksIn(fixture.chestContents))
    assertTrue(fixture.station.queue.isEmpty())
    assertTrue(fixture.station.held.isEmpty())
    verify { inventoryService.grantStacksTo(OWNER, ItemHolder.Chest(CHEST), mapOf(PLANK to 4)) }
  }

  @Test
  fun `without a chest beside it a cancelled entry's ingredients stay in the station`() {
    fixture.station = fixture.station.copy(queue = listOf(QueueEntry(RECIPE, 2)), held = mapOf(PLANK to 4))

    assertNull(service.cancelFromAfar(fixture.world, OWNER, STATION, index = 0))

    assertEquals(mapOf(PLANK to 4), fixture.station.held)
    assertEquals(OpError.CRAFT_NOT_POSSIBLE, service.cancelFromAfar(fixture.world, 99L, STATION, index = 0))
  }

  @Test
  fun `the owner reorders the queue from afar`() {
    val queue = listOf(QueueEntry(RECIPE, 1), QueueEntry(RECIPE, 2), QueueEntry(RECIPE, 3))
    fixture.station = fixture.station.copy(queue = queue)

    assertNull(service.moveFromAfar(OWNER, STATION, from = 2, to = 0))

    assertEquals(listOf(3, 1, 2), fixture.station.queue.map { it.amount })
    assertEquals(OpError.CRAFT_NOT_POSSIBLE, service.moveFromAfar(OWNER, STATION, from = 0, to = 5))
  }

  @Test
  fun `the owner picks the worker from afar`() {
    val bestia = fixture.givenBestia()

    assertNull(service.setWorkerFromAfar(fixture.world, OWNER, STATION, bestia, anyFreeCrafter = false))
    assertEquals(bestia, fixture.workers.entityOf(STATION))

    assertNull(service.setWorkerFromAfar(fixture.world, OWNER, STATION, null, anyFreeCrafter = true))
    assertTrue(fixture.station.waitsForCrafter)
    assertNull(fixture.workers.entityOf(STATION))
  }

  @Test
  fun `a station that is knocked down drops what it holds`() {
    fixture.station = fixture.station.copy(held = mapOf(PLANK to 4))

    service.spill(fixture.world, fixture.station, Vec3L(3, 3, 0))

    verify { lootItems.spawnLootItem(fixture.world, itemId = PLANK, amount = 4, pos = Vec3L(3, 3, 0)) }
  }

  @Test
  fun `the owner sets one of their bestias to work the station`() {
    val owner = fixture.givenOwner(planks = 0)
    val bestia = fixture.givenBestia()

    assertNull(service.setWorker(fixture.world, owner, STATION, bestia))

    assertEquals(StationFixture.PLAYER_BESTIA, fixture.station.workerBestiaId)
    assertEquals(bestia, fixture.workers.entityOf(STATION))
  }

  @Test
  fun `letting any free crafter take the station sends its bestia off it`() {
    val owner = fixture.givenOwner(planks = 0)
    service.setWorker(fixture.world, owner, STATION, fixture.givenBestia())

    assertNull(service.takeAnyFreeCrafter(fixture.world, owner, STATION))

    assertTrue(fixture.station.waitsForCrafter)
    assertNull(fixture.workers.entityOf(STATION))
  }

  @Test
  fun `picking a worker again stops waiting for a free crafter`() {
    val owner = fixture.givenOwner(planks = 0)
    service.takeAnyFreeCrafter(fixture.world, owner, STATION)

    service.setWorker(fixture.world, owner, STATION, 0L)

    assertFalse(fixture.station.takesAnyCrafter)
  }

  @Test
  fun `somebody else's bestia cannot be set to work it`() {
    val owner = fixture.givenOwner(planks = 0)
    val stranger = fixture.world.createEntity { id ->
      add(id, net.bestia.zone.identity.ecs.OwnedBestia(masterId = 99L, playerBestiaId = 77L))
    }

    assertEquals(OpError.CRAFT_NOT_POSSIBLE, service.setWorker(fixture.world, owner, STATION, stranger))
  }
}
