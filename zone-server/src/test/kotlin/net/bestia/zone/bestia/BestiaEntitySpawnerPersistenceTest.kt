package net.bestia.zone.bestia

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ai.ecs.AiAgentFactory
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.persistence.Persistent
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.profile.MovementProfileRegistry
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Whether a spawned creature gets a database row.
 *
 * The default has to stay `true`: a den's pack is rebuilt from storage at boot, and a default of `false`
 * would stop persisting packs while looking like it worked - the population would then grow on every
 * restart, which is a bug `SpawnerSystem` records having shipped once.
 *
 * The species here declares no AI profile, so `attachAi` returns before touching the agent factory and the
 * fixture stays about persistence.
 */
class BestiaEntitySpawnerPersistenceTest {

  private val catalogue = mockk<BestiaCatalogue>()

  private val sut = BestiaEntitySpawner(
    bestiaCatalogue = catalogue,
    aiProfileRegistry = mockk<AiProfileRegistry>(relaxed = true),
    aiAgentFactory = mockk<AiAgentFactory>(relaxed = true),
    movementProfileRegistry = MovementProfileRegistry().apply { load() }
  )

  init {
    every { catalogue.byId(1L) } returns Bestia(
      id = 1,
      identifier = "blob",
      level = 3,
      experienceReward = 5,
      health = 10,
      mana = 8
    )
  }

  @Test
  fun `spawnMob persists the creature by default`() {
    val world = testWorld()

    val id = sut.spawnMob(world, bestiaId = 1L, pos = Vec3L(1, 2, 3))

    assertTrue(world.has(id, Persistent::class))
  }

  @Test
  fun `spawnMob with persistent false leaves the creature transient`() {
    val world = testWorld()

    val id = sut.spawnMob(world, bestiaId = 1L, pos = Vec3L(1, 2, 3), persistent = false)

    assertFalse(world.has(id, Persistent::class))
  }
}
