package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.bestia.diary.BestiaHappened.Kind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.care.TestBagCare.BREAD
import net.bestia.zone.item.care.TestBagCare.bagWith
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A bestia below its eat line with nothing to eat goes home to wait, and its master is told once. */
class OutOfFoodTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
    ai.world.registerSystems(listOf(OwnedBestiaWatchSystem(ai.news)))
  }

  private fun owned(stamina: Int): EntityId {
    val id = ai.spawnMob("passive_wanderer", home, orders = AiConfig(BestiaJob.REST))
    ai.world.add(id, OwnedBestia(masterId = 7L, playerBestiaId = 1L))
    ai.world.add(id, Stamina(stamina, 100))
    return id
  }

  private fun kinds() = ai.told.map { it.kind }

  @Test
  fun `a hungry bestia with no food goes home and says so once`() {
    val bestia = owned(stamina = 20)
    ai.teleport(bestia, Vec3L(15, 0, 0))

    ai.tickUntilGoal(bestia, "RestAtHome")
    ai.tickUntil(describe = { "it stopped at ${ai.positionOf(bestia)}" }) { ai.positionOf(bestia).distance(home) <= 3 }
    ai.tick(times = 30)

    assertEquals(1, kinds().count { it == Kind.OUT_OF_FOOD })
  }

  @Test
  fun `a hungry bestia with bread eats instead`() {
    val bestia = owned(stamina = 20)
    ai.world.add(bestia, bagWith(BREAD to 1))

    ai.tick(times = 30)

    assertEquals(0, kinds().count { it == Kind.OUT_OF_FOOD })
  }
}
