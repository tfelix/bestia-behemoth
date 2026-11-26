package net.bestia.zone.spawn.ecs

import net.bestia.zone.config.WorldRulesConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.LootClaim
import net.bestia.zone.item.ecs.LootProtection
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import net.bestia.zone.bestia.loot.LootDrop
import net.bestia.zone.bestia.persistence.LootItemRepository
import net.bestia.zone.item.loot.LootItemEntitySpawner

class MobLootSpawnerTest {

  private val repository = mockk<LootItemRepository>().also {
    every { it.findAllDrops() } returns listOf(
      LootDrop(bestiaId = 1L, itemId = 50L, dropChance = 10_000),
      LootDrop(bestiaId = 3L, itemId = 51L, dropChance = 5_000),
    )
  }

  private val sut = MobLootSpawner(repository, LootItemEntitySpawner(WorldRulesConfig(tickRate = 20), mockk(relaxed = true)))

  @Test
  fun `the drop table is read once, not once per kill`() {
    val world = testWorld()

    repeat(3) { sut.spawnLoot(world, bestiaId = 1L, pos = Vec3L(0, 0, 0), loot = null) }

    verify(exactly = 1) { repository.findAllDrops() }
  }

  @Test
  fun `a certain drop lands on the ground, a species without a table drops nothing`() {
    val world = testWorld()

    val dropped = sut.spawnLoot(world, bestiaId = 1L, pos = Vec3L(0, 0, 0), loot = null)
    val none = sut.spawnLoot(world, bestiaId = 2L, pos = Vec3L(0, 0, 0), loot = null)

    assertEquals(50L, world.get(dropped.single(), GroundItemStack::class)?.itemId)
    assertEquals(emptyList(), none)
  }

  @Test
  fun `kill loot is protected for the side that won it`() {
    val world = testWorld()
    val loot = KillLootClaims.KillLoot(leadAccount = 9L) { listOf(LootClaim(setOf(9L, 10L), 6f)) }

    val dropped = sut.spawnLoot(world, bestiaId = 1L, pos = Vec3L(0, 0, 0), loot).single()

    val protection = world.get(dropped, LootProtection::class)
    assertEquals(setOf(9L, 10L), protection?.holders)
    assertEquals(6f, protection?.remainingSeconds)
  }

  @Test
  fun `a chance factor raises every drop's chance`() {
    val world = testWorld()

    val dropped = (1..50).flatMap {
      sut.spawnLoot(world, bestiaId = 3L, pos = Vec3L(0, 0, 0), loot = null, chanceFactor = 2.0)
    }

    assertEquals(50, dropped.size, "a 50% drop doubled always drops")
  }
}
