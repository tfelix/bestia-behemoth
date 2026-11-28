package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.bestia.diary.BestiaHappened.Kind
import net.bestia.zone.bestia.diary.BestiaState
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.travel.Journey
import net.bestia.zone.travel.JourneyOrder
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** What an owned bestia's master reads about a fight: the start of it, joining one, and a retreat. */
class OwnedBestiaNewsTest {

  private lateinit var ai: AiPipelineFixture
  private var nowMs = 0L

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
    ai.world.registerSystems(listOf(OwnedBestiaWatchSystem(ai.news, now = { nowMs })))
  }

  private fun owned(health: Int = 10): EntityId {
    val id = ai.spawnMob("passive_wanderer", Vec3L(0, 0, 0), health = health, orders = AiConfig(BestiaJob.REST))
    ai.world.add(id, OwnedBestia(masterId = 7L, playerBestiaId = 1L))
    return id
  }

  private fun kinds() = ai.told.map { it.kind }

  private fun statesTold() = ai.told.filter { it.kind == Kind.STATE }.map { it.reason }

  /** Ticks the world for [seconds], moving the watch's clock along with it. */
  private fun live(seconds: Int) {
    repeat(seconds) {
      nowMs += 1_000
      ai.tick(times = 20)
    }
  }

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
  fun `joining the master's fight is told once`() {
    owned()
    val master = ai.spawnPlayer(Vec3L(3, 0, 0))
    ai.world.getOrThrow(master, Master::class).masterId = 7L
    val foe = ai.spawnMob("passive_wanderer", Vec3L(5, 0, 0))

    repeat(3) {
      ai.recordHit(master, foe)
      live(seconds = 2)
    }

    assertEquals(1, kinds().count { it == Kind.HELPED })
  }

  @Test
  fun `a retreat is told when it begins`() {
    val bestia = owned(health = 3)
    ai.teleport(bestia, Vec3L(15, 0, 0))

    ai.tickUntilGoal(bestia, "Retreat")
    ai.tick(times = 30)

    assertEquals(1, kinds().count { it == Kind.RETREATED })
  }

  @Test
  fun `a state is told once it has lasted, and a short one not at all`() {
    val bestia = owned()

    live(seconds = 70)
    assertEquals(listOf(BestiaState.RESTING.name), statesTold())

    ai.world.add(bestia, Journey(listOf(Vec3L(40, 0, 0)), JourneyOrder.PLAYER))
    live(seconds = 10)
    ai.world.remove(bestia, Journey::class)
    live(seconds = 70)
    assertEquals(listOf(BestiaState.RESTING.name), statesTold())

    ai.world.add(bestia, Journey(listOf(Vec3L(40, 0, 0)), JourneyOrder.PLAYER))
    live(seconds = 70)
    assertEquals(listOf(BestiaState.RESTING.name, BestiaState.TRAVELLING.name), statesTold())
  }
}
