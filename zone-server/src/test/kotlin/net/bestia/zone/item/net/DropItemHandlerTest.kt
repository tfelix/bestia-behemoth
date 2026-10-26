package net.bestia.zone.item.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.loot.LootItemEntitySpawner
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The database removal comes first and gates the ground item, so a drop can never copy an item. Both halves have
 * to take the same thing, or the item ends up on the ground and in the bag at once.
 */
class DropItemHandlerTest {

  private val world = testWorld()
  private val inventoryService = mockk<InventoryService>()
  private val spawner = mockk<LootItemEntitySpawner>(relaxed = true)

  /** Holds the submitted job instead of running it, so the test decides when the database answers. */
  private var pending: (() -> Unit)? = null
  private val executor = mockk<AsyncJobExecutor>().also {
    every { it.submit(MASTER_ID, any()) } answers { pending = secondArg() }
  }

  private val arrows = Inventory.Item(itemId = ARROW, amount = 10)

  private val entity = world.createEntity { id ->
    add(id, Position(5, 5, 0))
    add(id, Inventory(mutableListOf(arrows)))
    add(id, Equipment(availableSlotMask = EquipmentSlots.ALL))
  }

  private val connections = ConnectionInfoService().also {
    it.activateSession(ACCOUNT_ID, masterId = MASTER_ID, masterEntityId = entity)
  }

  private val sut = DropItemHandler(inventoryService, spawner, connections, DeadActionGuard(), executor, world)

  private val drop = DropItemCMSG(playerId = ACCOUNT_ID, playerItemId = arrows.playerItemId, amount = 4)

  @Test
  fun `the handler only queues the drop, the database is not touched on its thread`() {
    sut.handle(world, drop)

    assertNotNull(pending)
    verify(exactly = 0) { inventoryService.removeNamedFromOwner(any(), any(), any(), any(), any(), any()) }
    verify(exactly = 0) { spawner.spawnLootItem(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a removal the database refuses spawns nothing and leaves the bag alone`() {
    every { inventoryService.removeNamedFromOwner(MASTER_ID, null, ARROW, 4, 0L, null) } returns null

    sut.handle(world, drop)
    pending!!.invoke()

    verify(exactly = 0) { spawner.spawnLootItem(any(), any(), any(), any(), any(), any()) }
    assertEquals(10, world.get(entity, Inventory::class)!!.getItem(ARROW.toInt())?.amount)
  }

  @Test
  fun `a removal the live bag cannot mirror spawns nothing`() {
    every { inventoryService.removeNamedFromOwner(MASTER_ID, null, ARROW, 4, 0L, null) } returns
      ItemContainer.RemovedItem(uniqueId = 0L, instance = null)

    sut.handle(world, drop)
    // Used up in between, by a job that ran first.
    world.get(entity, Inventory::class)!!.clearItems()
    pending!!.invoke()

    verify(exactly = 0) { spawner.spawnLootItem(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a confirmed removal leaves the bag and lands on the ground`() {
    every { inventoryService.removeNamedFromOwner(MASTER_ID, null, ARROW, 4, 0L, null) } returns
      ItemContainer.RemovedItem(uniqueId = 0L, instance = null)

    sut.handle(world, drop)
    pending!!.invoke()

    assertEquals(6, world.get(entity, Inventory::class)!!.getItem(ARROW.toInt())?.amount)
    verify { spawner.spawnLootItem(world, itemId = ARROW, amount = 4, pos = any(), uniqueId = 0L) }
  }

  @Test
  fun `a bestia drops from its own container`() {
    val bestiaArrows = Inventory.Item(itemId = ARROW, amount = 10)
    val bestia = world.createEntity { id ->
      add(id, Position(5, 5, 0))
      add(id, Inventory(mutableListOf(bestiaArrows)))
    }
    connections.registerPlayerBestiaEntity(ACCOUNT_ID, MASTER_ID, PLAYER_BESTIA_ID, bestia)
    connections.activateEntity(ACCOUNT_ID, bestia)
    every { inventoryService.removeNamedFromOwner(MASTER_ID, PLAYER_BESTIA_ID, ARROW, 4, 0L, null) } returns
      ItemContainer.RemovedItem(uniqueId = 0L, instance = null)

    sut.handle(world, drop.copy(playerItemId = bestiaArrows.playerItemId))
    pending!!.invoke()

    assertEquals(6, world.get(bestia, Inventory::class)!!.getItem(ARROW.toInt())?.amount)
  }

  @Test
  fun `more than the bag holds is refused before anything is queued`() {
    sut.handle(world, drop.copy(amount = 11))

    assertNull(pending)
  }

  /** Two copies of one template can differ, so the one the player dragged is the one that leaves. */
  @Test
  fun `a named copy is dropped by its own id`() {
    every { inventoryService.removeNamedFromOwner(MASTER_ID, null, SWORD, 1, 77L, null) } returns
      ItemContainer.RemovedItem(uniqueId = 77L, instance = null)
    val inventory = world.get(entity, Inventory::class)!!
    inventory.addItem(Inventory.Item(SWORD, amount = 1, uniqueId = 77L, stackable = false))
    inventory.addItem(Inventory.Item(SWORD, amount = 1, uniqueId = 78L, stackable = false))

    sut.handle(world, DropItemCMSG(ACCOUNT_ID, playerItemId = 77L, amount = 1))
    pending!!.invoke()

    assertEquals(listOf(78L), inventory.getItems().filter { it.itemId == SWORD }.map { it.uniqueId })
    verify { spawner.spawnLootItem(world, itemId = SWORD, amount = 1, pos = any(), uniqueId = 77L) }
  }

  /** Worn a moment ago, so the database still has it free: only the live Equipment knows it is worn. */
  @Test
  fun `worn gear is never dropped`() {
    world.get(entity, Inventory::class)!!.addItem(Inventory.Item(SWORD, amount = 1, uniqueId = 77L, stackable = false))
    world.get(entity, Equipment::class)!!
      .equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(itemId = SWORD, uniqueId = 77L))

    sut.handle(world, DropItemCMSG(ACCOUNT_ID, playerItemId = 77L, amount = 1))

    assertNull(pending)
  }

  @Test
  fun `a dead master drops nothing`() {
    world.add(entity, Dead())

    sut.handle(world, drop)

    assertNull(pending)
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 9L
    const val PLAYER_BESTIA_ID = 21L
    const val ARROW = 40L
    const val SWORD = 11L
  }
}
