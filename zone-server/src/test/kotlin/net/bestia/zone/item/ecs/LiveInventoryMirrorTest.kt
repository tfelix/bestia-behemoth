package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.stat.ItemStat
import net.bestia.zone.item.stat.StatValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveInventoryMirrorTest {

  private lateinit var world: EcsWorld
  private lateinit var inventory: Inventory

  @BeforeEach
  fun setUp() {
    world = testWorld()
    inventory = Inventory(mutableListOf())
  }

  @Test
  fun `a granted instance keeps its wear and its rolled stats`() {
    val holder = world.createEntity { id -> add(id, inventory) }
    val sword = reserved(uniqueId = 77L, stackable = false, durability = 12, stats = listOf(StatValue(ItemStat.STR, 3)))

    world.grantToLive(holder, listOf(sword))

    val item = inventory.getItems().single()
    assertEquals(77L, item.uniqueId)
    assertEquals(12, item.durability)
    assertEquals(1, item.rolledStats.size)
  }

  @Test
  fun `taking a stack takes only that many`() {
    val holder = world.createEntity { id -> add(id, inventory) }
    inventory.addItem(Inventory.Item(itemId = 5L, amount = 10))

    assertTrue(world.takeFromLive(holder, reserved(itemId = 5L, amount = 4)))
    assertEquals(6, inventory.getItems().single().amount)
  }

  @Test
  fun `taking a made stack leaves the plain pile alone`() {
    val holder = world.createEntity { id -> add(id, inventory) }
    val alice = ItemProvenance("Alice", "Genesis")
    inventory.addItem(Inventory.Item(itemId = 5L, amount = 3))
    inventory.addItem(Inventory.Item(itemId = 5L, amount = 3, provenance = alice))

    assertTrue(world.takeFromLive(holder, reserved(itemId = 5L, amount = 3, provenance = alice)))
    assertEquals(listOf(null), inventory.getItems().map { it.provenance })
  }

  @Test
  fun `taking what the live inventory does not hold says so`() {
    val holder = world.createEntity { id -> add(id, inventory) }

    assertFalse(world.takeFromLive(holder, reserved(uniqueId = 77L, stackable = false)))
  }

  @Test
  fun `an offline holder is skipped, not failed`() {
    val offline = world.createEntity { }

    world.grantToLive(offline, listOf(reserved()))
    assertFalse(world.takeFromLive(offline, reserved()))
  }

  private fun reserved(
    itemId: Long = 1L,
    amount: Int = 1,
    uniqueId: Long = 0L,
    stackable: Boolean = true,
    durability: Int = 0,
    stats: List<StatValue> = emptyList(),
    provenance: ItemProvenance? = null,
  ): ReservedItem {
    return ReservedItem(
      offerSlotId = 1L, itemId = itemId, amount = amount, weight = 1, uniqueId = uniqueId, stackable = stackable,
      durability = durability, maxDurability = 20, slots = 0, upgradeLevel = 0, itemLevel = 1, rolledStats = stats,
      provenance = provenance,
    )
  }
}
