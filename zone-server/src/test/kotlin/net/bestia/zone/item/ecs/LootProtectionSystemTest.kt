package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LootProtectionSystemTest {

  private val world = testWorld(systems = listOf(LootProtectionSystem()))

  private val loot = world.createEntity { id -> add(id, LootProtection(ownerAccountId = 1L, remainingSeconds = 6f)) }

  @Test
  fun `kill loot stays protected until its time is up`() {
    repeat(5) { world.tick(1f) }

    assertTrue(world.has(loot, LootProtection::class))
  }

  @Test
  fun `and is free for everyone after`() {
    repeat(6) { world.tick(1f) }

    assertFalse(world.has(loot, LootProtection::class))
  }

  @Test
  fun `a claim hands over to the next one before the drop goes free`() {
    val turn = world.createEntity { id ->
      add(id, LootProtection(listOf(LootClaim(setOf(1L), 3f), LootClaim(setOf(1L, 2L), 6f))))
    }

    repeat(3) { world.tick(1f) }
    assertEquals(setOf(1L, 2L), world.get(turn, LootProtection::class)?.holders)

    repeat(6) { world.tick(1f) }
    assertFalse(world.has(turn, LootProtection::class))
  }
}
