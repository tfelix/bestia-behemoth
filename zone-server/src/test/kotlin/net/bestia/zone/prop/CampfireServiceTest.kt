package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.chest.ChestStorages
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class CampfireServiceTest {

  private val now = Instant.parse("2026-11-10T12:00:00Z")
  private val world: EcsWorld = testWorld()

  private val structures = mockk<PlayerStructureRegistry>(relaxed = true)
  private val inventoryService = mockk<InventoryService>().also {
    every { it.consumeAll(any(), any(), any()) } returns true
    every { it.consumeAllFrom(any(), any()) } returns true
  }
  private val chestContents = Inventory(mutableListOf())
  private val service = CampfireService(
    structures = structures,
    itemTemplates = mockk<ItemTemplateRegistry> { every { idOf("log") } returns LOG },
    inventoryService = inventoryService,
    asyncJobExecutor = mockk<AsyncJobExecutor> {
      every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
    },
    chests = mockk<ChestStorages> { every { contentsOf(CHEST) } returns chestContents },
    clock = Clock.fixed(now, ZoneOffset.UTC)
  )

  @Test
  fun `the owner puts a log on and the fire burns half an hour longer`() {
    givenFire(burnsOutAt = now + Duration.ofMinutes(10))
    val owner = givenMaster(OWNER, logs = 3)

    assertNull(service.addWood(world, owner, FIRE))

    verify { structures.setBurnsOutAt(FIRE, now + Duration.ofMinutes(40)) }
    assertEquals(2, world.get(owner, Inventory::class)!!.getItems().sumOf { it.amount })
    verify { inventoryService.consumeAll(OWNER, listOf(LOG to 1)) }
  }

  @Test
  fun `somebody else's fire is refused`() {
    givenFire(burnsOutAt = now)
    val stranger = givenMaster(masterId = 99L, logs = 3)

    assertEquals(OpError.CRAFT_NOT_POSSIBLE, service.addWood(world, stranger, FIRE))
  }

  @Test
  fun `a fire that holds two hours takes no more wood and keeps the log`() {
    givenFire(burnsOutAt = now + Duration.ofHours(2))
    val owner = givenMaster(OWNER, logs = 1)

    assertEquals(OpError.CRAFT_NOT_POSSIBLE, service.addWood(world, owner, FIRE))
    assertEquals(1, world.get(owner, Inventory::class)!!.getItems().sumOf { it.amount })
  }

  @Test
  fun `no log in the bag is said so`() {
    givenFire(burnsOutAt = now)
    val owner = givenMaster(OWNER, logs = 0)

    assertEquals(OpError.CRAFT_MISSING_MATERIALS, service.addWood(world, owner, FIRE))
  }

  @Test
  fun `a fire left cold too long is gone and cannot be lit again`() {
    givenFire(burnsOutAt = now - Duration.ofMinutes(31))
    val owner = givenMaster(OWNER, logs = 1)

    assertEquals(OpError.CRAFT_NOT_POSSIBLE, service.addWood(world, owner, FIRE))
  }

  @Test
  fun `a fire that runs low gets a log from the chest next to it`() {
    val fire = givenFire(burnsOutAt = now + Duration.ofMinutes(2))
    givenChest(logs = 2)

    assertTrue(service.addWoodFromChest(world, FIRE))

    verify { structures.setBurnsOutAt(FIRE, now + Duration.ofMinutes(32)) }
    assertEquals(1, chestContents.getItems().sumOf { it.amount })
    verify { inventoryService.consumeAllFrom(OWNER, mapOf(ItemHolder.Chest(CHEST) to mapOf(LOG to 1))) }
    verify { structures.chestBeside(fire) }
  }

  @Test
  fun `a fire with time left or no wood in the chest gets no log`() {
    givenFire(burnsOutAt = now + Duration.ofMinutes(20))
    givenChest(logs = 2)
    assertFalse(service.addWoodFromChest(world, FIRE))

    givenFire(burnsOutAt = now)
    chestContents.clearItems()
    assertFalse(service.addWoodFromChest(world, FIRE))
    verify(exactly = 0) { structures.setBurnsOutAt(any(), any()) }
  }

  private fun givenFire(burnsOutAt: Instant): StructureEntry {
    val fire =
      StructureEntry(FIRE, StaticEntityKind.CAMPFIRE, OWNER, 1L, Vec3L(0, 0, 0), yaw = 0f, burnsOutAt = burnsOutAt)
    every { structures.of(FIRE) } returns fire
    return fire
  }

  private fun givenChest(logs: Int) {
    every { structures.chestBeside(any()) } returns
      StructureEntry(CHEST, StaticEntityKind.CHEST, OWNER, 1L, Vec3L(2, 0, 0), yaw = 0f)
    chestContents.addItem(Inventory.Item(itemId = LOG, amount = logs, weight = 1))
  }

  private fun givenMaster(masterId: Long, logs: Int): EntityId {
    return world.createEntity { id ->
      add(id, Master(masterId = masterId, name = "Cook"))
      add(id, Position(1, 0, 0))
      val items = if (logs > 0) mutableListOf(Inventory.Item(itemId = LOG, amount = logs, weight = 1)) else mutableListOf()
      add(id, Inventory(items))
    }
  }

  private companion object {
    const val FIRE = 5L
    const val OWNER = 42L
    const val LOG = 43L
    const val CHEST = 6L
  }
}
