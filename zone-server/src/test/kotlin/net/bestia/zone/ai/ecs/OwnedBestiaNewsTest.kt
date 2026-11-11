package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.bestia.diary.BestiaHappened.Kind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** What an owned bestia's master reads about a fight: the start of it, and a retreat. */
class OwnedBestiaNewsTest {

  private lateinit var ai: AiPipelineFixture

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
    ai.world.registerSystems(listOf(OwnedBestiaWatchSystem(ai.news)))
  }

  private fun owned(health: Int = 10): EntityId {
    val id = ai.spawnMob("passive_wanderer", Vec3L(0, 0, 0), health = health, orders = AiConfig(BestiaJob.REST))
    ai.world.add(id, OwnedBestia(masterId = 7L, playerBestiaId = 1L))
    return id
  }

  private fun kinds() = ai.told.map { it.kind }

  @Test
  fun `a fight is told once, not every blow`() {
    val bestia = owned()
    val stranger = ai.spawnPlayer(Vec3L(30, 0, 0))

    repeat(3) {
      ai.recordHit(bestia, stranger)
      ai.tick(times = 30)
    }

    assertEquals(listOf(Kind.ATTACKED), kinds().filter { it == Kind.ATTACKED })
  }

  @Test
  fun `a retreat is told when it begins`() {
    val bestia = owned(health = 3)
    ai.teleport(bestia, Vec3L(15, 0, 0))

    ai.tickUntilGoal(bestia, "Retreat")
    ai.tick(times = 30)

    assertEquals(1, kinds().count { it == Kind.RETREATED })
  }
}
