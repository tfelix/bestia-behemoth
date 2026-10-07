package net.bestia.zone.spawn.ecs

import net.bestia.zone.config.WorldRulesConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.LootProtection
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import net.bestia.zone.bestia.loot.LootDrop
import net.bestia.zone.bestia.persistence.LootItemRepository
import net.bestia.zone.item.loot.LootItemEntitySpawner

class MobLootSpawnerTest {

  private val repository = mockk<LootItemRepository>().also {
    every { it.findAllDrops() } returns listOf(LootDrop(bestiaId = 1L, itemId = 50L, dropChance = 10_000))
  }

  private val sut = MobLootSpawner(repository, LootItemEntitySpawner(WorldRulesConfig(tickRate = 20)))

  @Test
  fun `the drop table is read once, not once per kill`() {
    val world = testWorld()

    repeat(3) { sut.spawnLoot(world, bestiaId = 1L, pos = Vec3L(0, 0, 0), lootOwner = null) }

    verify(exactly = 1) { repository.findAllDrops() }
  }

  @Test
  fun `a certain drop lands on the ground, a species without a table drops nothing`() {
    val world = testWorld()

    val dropped = sut.spawnLoot(world, bestiaId = 1L, pos = Vec3L(0, 0, 0), lootOwner = null)
    val none = sut.spawnLoot(world, bestiaId = 2L, pos = Vec3L(0, 0, 0), lootOwner = null)

    assertEquals(50L, world.get(dropped.single(), GroundItemStack::class)?.itemId)
    assertEquals(emptyList(), none)
  }

  @Test
  fun `kill loot is protected for its killer for the configured time`() {
    val world = testWorld()

    val dropped = sut.spawnLoot(world, bestiaId = 1L, pos = Vec3L(0, 0, 0), lootOwner = 9L).single()

    val protection = world.get(dropped, LootProtection::class)
    assertEquals(9L, protection?.ownerAccountId)
    assertEquals(6f, protection?.remainingSeconds)
  }
}
