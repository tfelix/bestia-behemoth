package net.bestia.zone.spawn.ecs

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.persistence.ItemInstance
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BestiaBagDropSystemTest {

  private val world = testWorld()
  private val inventoryService = mockk<InventoryService>()
  private val spawner = mockk<LootItemEntitySpawner>(relaxed = true)

  /** Holds the submitted jobs instead of running them, so the test decides when the database answers. */
  private val pending = mutableListOf<() -> Unit>()
  private val executor = mockk<AsyncJobExecutor>().also {
    every { it.submit(MASTER_ID, any()) } answers { pending += secondArg<() -> Unit>() }
  }

  private val sut = BestiaBagDropSystem(inventoryService, spawner, executor, world)

  private val arrow = Item(id = ARROW, identifier = "arrow", weight = 1, type = Item.ItemType.ETC, material = ItemMaterial.WOOD)
  private val sword = Item(
    id = SWORD, identifier = "sword", weight = 10, type = Item.ItemType.EQUIP,
    equipSlot = EquipmentSlot.RIGHT_HAND,
    material = ItemMaterial.METAL
  )

  private val bestia = world.createEntity { id ->
    add(id, Position(5, 6, 0))
    add(id, OwnedBestia(masterId = MASTER_ID, playerBestiaId = PLAYER_BESTIA_ID))
    add(id, Account(OWNER_ACCOUNT))
    add(
      id,
      Inventory(
        mutableListOf(
          Inventory.Item(itemId = ARROW, amount = 10),
          Inventory.Item(itemId = SWORD, amount = 1, uniqueId = SWORD_INSTANCE, stackable = false),
        )
      )
    )
  }

  private fun databaseGivesUpTheBag() {
    every { inventoryService.removeAllFreeFromOwner(MASTER_ID, PLAYER_BESTIA_ID) } returns listOf(
      ItemContainer.DetachedItem(arrow, 10, instance = null),
      ItemContainer.DetachedItem(sword, 1, ItemInstance(item = sword, id = SWORD_INSTANCE)),
    )
  }

  private fun killedBy(accountId: Long) {
    val killer = world.createEntity { id -> add(id, Account(accountId)) }
    world.add(bestia, TakenDamage().apply { addDamage(killer, 10) })
    world.add(bestia, Dead())
  }

  private fun runJobs() {
    pending.forEach { it() }
    pending.clear()
  }

  @Test
  fun `the bag lands where the bestia fell as loot of its killer`() {
    databaseGivesUpTheBag()
    killedBy(KILLER_ACCOUNT)

    sut.update(world, 0f)
    runJobs()

    assertTrue(world.get(bestia, Inventory::class)!!.isEmpty())
    verify { spawner.spawnLootItem(world, ARROW, 10, Vec3L(5, 6, 0), uniqueId = 0L, lootOwner = KILLER_ACCOUNT) }
    verify { spawner.spawnLootItem(world, SWORD, 1, Vec3L(5, 6, 0), uniqueId = SWORD_INSTANCE, lootOwner = KILLER_ACCOUNT) }
  }

  @Test
  fun `a bestia nobody killed drops loot anyone may take`() {
    databaseGivesUpTheBag()
    world.add(bestia, Dead())

    sut.update(world, 0f)
    runJobs()

    verify { spawner.spawnLootItem(world, ARROW, 10, any(), uniqueId = 0L, lootOwner = null) }
  }

  @Test
  fun `the bag drops once while the body lies there`() {
    databaseGivesUpTheBag()
    killedBy(KILLER_ACCOUNT)

    sut.update(world, 0f)
    sut.update(world, 0f)

    assertEquals(1, pending.size)
  }

  /** The respawn moves the body and clears its damage, but the bag belongs where it fell and to who felled it. */
  @Test
  fun `a respawn before the database answers still drops at the body`() {
    databaseGivesUpTheBag()
    killedBy(KILLER_ACCOUNT)

    sut.update(world, 0f)
    world.add(bestia, Position(50, 60, 0))
    world.remove(bestia, TakenDamage::class)
    world.remove(bestia, Dead::class)
    runJobs()

    verify { spawner.spawnLootItem(world, ARROW, 10, Vec3L(5, 6, 0), uniqueId = 0L, lootOwner = KILLER_ACCOUNT) }
  }

  /** Worn gear and promised items are left in the container by the database, so they stay in the live bag too. */
  @Test
  fun `what the database keeps stays in the bag`() {
    every { inventoryService.removeAllFreeFromOwner(MASTER_ID, PLAYER_BESTIA_ID) } returns
      listOf(ItemContainer.DetachedItem(arrow, 10, instance = null))
    killedBy(KILLER_ACCOUNT)

    sut.update(world, 0f)
    runJobs()

    val left = world.get(bestia, Inventory::class)!!.getItems()
    assertEquals(listOf(SWORD), left.map { it.itemId })
    verify(exactly = 0) { spawner.spawnLootItem(any(), SWORD, any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `an item the live bag does not hold is not dropped`() {
    databaseGivesUpTheBag()
    killedBy(KILLER_ACCOUNT)
    world.get(bestia, Inventory::class)!!.removeFromStack(ARROW, 10)

    sut.update(world, 0f)
    runJobs()

    verify(exactly = 0) { spawner.spawnLootItem(any(), ARROW, any(), any(), any(), any(), any(), any(), any()) }
    verify { spawner.spawnLootItem(world, SWORD, 1, any(), uniqueId = SWORD_INSTANCE, lootOwner = KILLER_ACCOUNT) }
  }

  @Test
  fun `a dead master keeps its bag`() {
    val master = world.createEntity { id ->
      add(id, Position(1, 1, 0))
      add(id, Account(OWNER_ACCOUNT))
      add(id, Inventory(mutableListOf(Inventory.Item(itemId = ARROW, amount = 3))))
      add(id, Dead())
    }

    sut.update(world, 0f)

    assertTrue(pending.isEmpty())
    assertEquals(3, world.get(master, Inventory::class)!!.getItem(ARROW.toInt())?.amount)
  }

  @Test
  fun `an empty bag drops nothing`() {
    every { inventoryService.removeAllFreeFromOwner(MASTER_ID, PLAYER_BESTIA_ID) } returns emptyList()
    killedBy(KILLER_ACCOUNT)

    sut.update(world, 0f)
    runJobs()

    verify(exactly = 0) { spawner.spawnLootItem(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
  }

  private companion object {
    const val MASTER_ID = 9L
    const val PLAYER_BESTIA_ID = 21L
    const val OWNER_ACCOUNT = 1L
    const val KILLER_ACCOUNT = 42L
    const val ARROW = 40L
    const val SWORD = 11L
    const val SWORD_INSTANCE = 77L
  }
}
