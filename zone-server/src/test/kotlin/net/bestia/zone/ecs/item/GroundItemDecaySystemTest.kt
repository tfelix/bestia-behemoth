package net.bestia.zone.ecs.item

import io.mockk.mockk
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.loot.LootItemEntitySpawner
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every dropped item is an entity that is streamed, persisted and loaded again on every start. Kept forever, a
 * player dropping items one by one could fill the world with them.
 */
class GroundItemDecaySystemTest {

  private val world = testWorld(systems = listOf(GroundItemDecaySystem()))
  private val spawner = LootItemEntitySpawner(mockk(), ZoneConfig(tickRate = 20, groundItemDespawnSeconds = 10f))

  @Test
  fun `a plain item disappears once its time on the ground is up`() {
    val item = spawner.spawnLootItem(world, itemId = 1L, amount = 3, pos = Vec3L(0, 0, 0))

    repeat(11) { world.tick(1f) }

    assertFalse(world.isAlive(item))
  }

  @Test
  fun `a plain item stays until then`() {
    val item = spawner.spawnLootItem(world, itemId = 1L, amount = 3, pos = Vec3L(0, 0, 0))

    repeat(5) { world.tick(1f) }

    assertTrue(world.isAlive(item))
  }

  /** A unique item is one of a kind, and its instance would be lost with it. */
  @Test
  fun `a unique item stays on the ground`() {
    val item = spawner.spawnLootItem(world, itemId = 1L, amount = 1, pos = Vec3L(0, 0, 0), uniqueId = 7L)

    repeat(11) { world.tick(1f) }

    assertTrue(world.isAlive(item))
  }
}
