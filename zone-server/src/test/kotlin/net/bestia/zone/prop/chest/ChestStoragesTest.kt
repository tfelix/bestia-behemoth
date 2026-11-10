package net.bestia.zone.prop.chest

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.ChestStorage
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.SpilledProperty
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChestStoragesTest {

  private val world = testWorld()
  private val structures = mockk<PlayerStructureRegistry> {
    every { of(CHEST_ID) } returns StructureEntry(
      CHEST_ID, StaticEntityKind.CHEST, ownerMasterId = 9L, ownerAccountId = OWNER, position = Vec3L(5, 5, 0), yaw = 0f
    )
  }
  private val sut = ChestStorages(
    structures, mockk(), ItemStatCatalog(), LootItemEntitySpawner(WorldRulesConfig(tickRate = 20), mockk(relaxed = true)),
    world,
  )

  @Test
  fun `a chest's prop gets the same contents each time it spawns`() {
    val first = world.createEntity { id -> sut.furnish(this, id, CHEST_ID) }
    val second = world.createEntity { id -> sut.furnish(this, id, CHEST_ID) }

    assertSame(world.get(first, Inventory::class), world.get(second, Inventory::class))
    assertEquals(ChestStorage(CHEST_ID, ownerMasterId = 9L, ownerAccountId = OWNER), world.get(second, ChestStorage::class))
  }

  @Test
  fun `a fallen chest spills every pile as its owner's property`() {
    sut.contentsOf(CHEST_ID).addItem(Inventory.Item(itemId = 40L, amount = 7))
    sut.contentsOf(CHEST_ID).addItem(Inventory.Item(itemId = 11L, amount = 1, uniqueId = 77L, itemLevel = 34))

    sut.spill(CHEST_ID, OWNER, Vec3L(5, 5, 0))
    world.runPostedUntil(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50))

    val lying = world.query(GroundItemStack::class, SpilledProperty::class)
      .map { get<GroundItemStack>() to get<SpilledProperty>() }
    assertEquals(setOf(40L to 7, 11L to 1), lying.map { (stack, _) -> stack.itemId to stack.amount }.toSet())
    assertEquals(77L, lying.single { (stack, _) -> stack.itemId == 11L }.first.uniqueId)
    assertEquals(34, lying.single { (stack, _) -> stack.itemId == 11L }.second.itemLevel)
    assertTrue(lying.all { (_, spilled) -> spilled.ownerAccountId == OWNER })
    assertTrue(sut.contentsOf(CHEST_ID).isEmpty(), "the chest is forgotten")
  }

  private companion object {
    const val CHEST_ID = 5L
    const val OWNER = 3L
  }
}
