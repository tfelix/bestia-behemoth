package net.bestia.zone.ecs.item

import io.mockk.mockk
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.loot.LootItemEntitySpawner
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What players leave on the ground stays part of the world for days, but not forever: every dropped item is an
 * entity that is streamed, persisted and loaded again on every start.
 */
class GroundItemDecaySystemTest {

  private var now = Instant.parse("2026-10-05T12:00:00Z")
  private val clock = object : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: ZoneId) = this
    override fun instant() = now
  }

  private val world = testWorld(systems = listOf(GroundItemDecaySystem(clock)))
  private val spawner = LootItemEntitySpawner(
    mockk(),
    WorldRulesConfig(tickRate = 20, groundItemDespawnAfter = Duration.ofDays(7)),
    clock
  )

  @Test
  fun `a plain item disappears once its week on the ground is up`() {
    val item = spawner.spawnLootItem(world, itemId = 1L, amount = 3, pos = Vec3L(0, 0, 0))

    now = now.plus(Duration.ofDays(7))
    world.tick(60f)

    assertFalse(world.isAlive(item))
  }

  @Test
  fun `a plain item stays until then`() {
    val item = spawner.spawnLootItem(world, itemId = 1L, amount = 3, pos = Vec3L(0, 0, 0))

    now = now.plus(Duration.ofDays(6))
    world.tick(60f)

    assertTrue(world.isAlive(item))
  }

  /** A unique item is one of a kind, and its instance would be lost with it. */
  @Test
  fun `a unique item stays on the ground`() {
    val item = spawner.spawnLootItem(world, itemId = 1L, amount = 1, pos = Vec3L(0, 0, 0), uniqueId = 7L)

    now = now.plus(Duration.ofDays(30))
    world.tick(60f)

    assertTrue(world.isAlive(item))
  }
}
