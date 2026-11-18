package net.bestia.zone.quest

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.place.ecs.Place
import net.bestia.zone.place.ecs.PlaceRef
import net.bestia.zone.quest.ecs.ActiveQuests
import net.bestia.zone.quest.ecs.TrackedObjectives
import net.bestia.zone.quest.ecs.TrackedKill
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class QuestKillObserverTest {

  private val now = Instant.parse("2026-11-17T12:00:00Z")
  private val world = testWorld()
  private val questService = mockk<QuestService>(relaxed = true)

  // Runs each save at once, so the test sees what reached the service.
  private val jobs = mockk<AsyncJobExecutor> {
    val job = slot<() -> Unit>()
    every { submit(any(), capture(job)) } answers { job.captured() }
  }

  private val observer = QuestKillObserver(questService, jobs, Clock.fixed(now, ZoneOffset.UTC))

  @Test
  fun `a kill of the wanted species in the wanted place counts and is saved`() {
    val tracked = trackedKill(area = QuestArea.InPlace(TOWN))
    val master = masterIn(TOWN, tracked)

    observer.onKill(world, KillFact(WOLF, FAR_AWAY, setOf(master)))

    assertEquals(1, tracked.count)
    verify { questService.recordPersonal(MASTER_ID, QUEST_ID, 0, 1) }
  }

  @Test
  fun `another species, another place or a late kill counts nothing`() {
    val tracked = trackedKill(area = QuestArea.InPlace(TOWN))
    val inTown = masterIn(TOWN, tracked)
    val elsewhere = masterIn("Elsewhere", trackedKill(area = QuestArea.InPlace(TOWN)))
    val late = masterIn(TOWN, trackedKill(area = QuestArea.InPlace(TOWN), deadline = now.minusSeconds(1)))

    observer.onKill(world, KillFact(BOAR, FAR_AWAY, setOf(inTown)))
    observer.onKill(world, KillFact(WOLF, FAR_AWAY, setOf(elsewhere, late)))

    assertEquals(0, tracked.count)
    verify(exactly = 0) { questService.recordPersonal(any(), any(), any(), any()) }
  }

  @Test
  fun `a spot area counts kills inside its radius only`() {
    val tracked = trackedKill(area = QuestArea.Around(x = 100, y = 100, radius = 10))
    val master = masterIn(TOWN, tracked)

    observer.onKill(world, KillFact(WOLF, Vec3L(106, 108, 0), setOf(master)))
    observer.onKill(world, KillFact(WOLF, Vec3L(108, 108, 0), setOf(master)))

    assertEquals(1, tracked.count)
  }

  @Test
  fun `counting stops at the required number`() {
    val tracked = trackedKill(area = null, required = 2)
    val master = masterIn(TOWN, tracked)

    repeat(3) { observer.onKill(world, KillFact(WOLF, FAR_AWAY, setOf(master))) }

    assertEquals(2, tracked.count)
  }

  private fun trackedKill(area: QuestArea?, required: Int = 5, deadline: Instant = now.plus(Duration.ofDays(1))): TrackedKill {
    return TrackedKill(QUEST_ID, objectiveIndex = 0, speciesId = WOLF, area = area, required = required, deadline = deadline, count = 0)
  }

  private fun masterIn(place: String, tracked: TrackedKill): EntityId {
    return world.createEntity { id ->
      add(id, ActiveQuests(MASTER_ID, TrackedObjectives(kills = listOf(tracked))))
      add(id, Place(PlaceRef(place)))
    }
  }

  private companion object {
    const val MASTER_ID = 5L
    const val QUEST_ID = 70L
    const val WOLF = 3L
    const val BOAR = 4L
    const val TOWN = "Ashford"
    val FAR_AWAY = Vec3L(5_000, 5_000, 0)
  }
}
