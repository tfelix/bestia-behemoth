package net.bestia.zone.bestia

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ai.ecs.AiAgentFactory
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.entity.VisualKind
import net.bestia.zone.ecs.spawn.townsfolk.TownsfolkBody
import net.bestia.zone.ecs.spawn.townsfolk.TownsfolkVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.profile.MovementProfileRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * What a spawned creature is drawn as.
 *
 * The default is the load-bearing half: every mob but a townsperson relies on it, so a caller that passes
 * nothing must still get the species body. The override exists because one archetype stands in for a whole
 * town - see `TownsfolkEntitySpawner`.
 *
 * The species here declares no AI profile, so `attachAi` returns before touching the agent factory.
 */
class BestiaEntitySpawnerVisualTest {

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
  fun `a creature is drawn from its species by default`() {
    val world = testWorld()

    val id = sut.spawnMob(world, bestiaId = 1L, pos = Vec3L(1, 2, 3))

    assertEquals(EntityVisual(VisualKind.BESTIA, 1L), world.get(id, EntityVisual::class))
  }

  @Test
  fun `a caller with its own visual gets that one instead`() {
    val world = testWorld()
    val visual = TownsfolkVisual("Hilda", TownsfolkBody.ADULT)

    val id = sut.spawnMob(world, bestiaId = 1L, pos = Vec3L(1, 2, 3), visual = visual)

    assertEquals(visual, world.get(id, TownsfolkVisual::class))
    // The point of passing it in rather than swapping afterwards: no watcher ever sees the species body.
    assertNull(world.get(id, EntityVisual::class), "the archetype's body was put on as well")
    assertFalse(world.has(id, EntityVisual::class))
  }
}
