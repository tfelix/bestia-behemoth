package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.testWorld
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
}
