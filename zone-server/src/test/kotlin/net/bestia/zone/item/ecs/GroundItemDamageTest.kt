package net.bestia.zone.item.ecs

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.status.Invulnerable
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.material.ItemMaterialRegistry
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroundItemDamageTest {

  private val blueprint = Item(id = 16L, identifier = "blueprint", weight = 1, type = Item.ItemType.ETC, material = ItemMaterial.PAPER)
  private val sword = Item(id = 19L, identifier = "sword", weight = 120, type = Item.ItemType.ETC, material = ItemMaterial.METAL)

  private val itemRepository = mockk<ItemRepository> { every { findAll() } returns listOf(blueprint, sword) }
  private val deletionQueue = PersistedEntityDeletionQueue()

  private val sut = GroundItemDamage(
    itemTemplates = ItemTemplateRegistry(itemRepository),
    materials = ItemMaterialRegistry().also { it.load() },
    groundStackRemoval = GroundStackRemoval(deletionQueue),
  )

  private val spawner = LootItemEntitySpawner(WorldRulesConfig(tickRate = 20))

  private fun EcsWorld.stackOf(item: Item): EntityId {
    return spawner.spawnLootItem(this, itemId = item.id, amount = 5, pos = Vec3L(0, 0, 0))
  }

  @Test
  fun `fire burns paper in one hit`() {
    val world = testWorld()
    val stack = world.stackOf(blueprint)

    sut.damage(world, stack, 10, Element.FIRE)

    assertFalse(world.isAlive(stack))
    assertEquals(listOf(stack), deletionQueue.drainAll())
  }

  @Test
  fun `fire does nothing to metal`() {
    val world = testWorld()
    val stack = world.stackOf(sword)

    sut.damage(world, stack, 1000, Element.FIRE)

    assertEquals(0, world.get(stack, GroundItemIntegrity::class)!!.lost)
  }

  @Test
  fun `a hit at the hardness does nothing however often it lands`() {
    val world = testWorld()
    val stack = world.stackOf(sword)

    repeat(100) { sut.damage(world, stack, 15, Element.NORMAL) }

    assertEquals(0, world.get(stack, GroundItemIntegrity::class)!!.lost)
  }

  @Test
  fun `a destroyed stack vanishes as a death`() {
    val world = testWorld()
    val stack = world.stackOf(blueprint)
    var deadWhenDestroyed = false
    world.onDestroy { id -> if (id == stack) deadWhenDestroyed = world.has(id, Dead::class) }

    sut.damage(world, stack, 10, Element.FIRE)

    assertTrue(deadWhenDestroyed)
  }

  @Test
  fun `an invulnerable stack is untouched`() {
    val world = testWorld()
    val stack = world.stackOf(blueprint)
    world.add(stack, Invulnerable)

    sut.damage(world, stack, 1000, Element.FIRE)

    assertTrue(world.isAlive(stack))
    assertEquals(0, world.get(stack, GroundItemIntegrity::class)!!.lost)
  }

  /** Inside a system the destroy waits for the end of the tick, so the second hit still finds the entity. */
  @Test
  fun `a stack used up inside a system is destroyed once`() {
    var stack: EntityId = 0L
    val burnTwice = object : System {
      override val phase = Phase.COMBAT
      override val reads: ComponentClassSet = sut.reads
      override val writes: ComponentClassSet = sut.writes

      override fun update(world: World, deltaTime: Float) {
        sut.damage(world, stack, 10, Element.FIRE)
        sut.damage(world, stack, 10, Element.FIRE)
      }
    }
    val world = testWorld(systems = listOf(burnTwice))
    stack = world.stackOf(blueprint)

    world.tick(0.05f)

    assertFalse(world.isAlive(stack))
    assertEquals(listOf(stack), deletionQueue.drainAll())
  }
}
