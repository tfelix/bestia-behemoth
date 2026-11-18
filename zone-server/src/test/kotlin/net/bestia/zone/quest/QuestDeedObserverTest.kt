package net.bestia.zone.quest

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.observe.DeedFact
import net.bestia.zone.entity.observe.DeedFact.Deed
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactRef
import net.bestia.zone.fact.ecs.KnownFacts
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.quest.ecs.ActiveQuests
import net.bestia.zone.quest.ecs.TrackedDeed
import net.bestia.zone.quest.ecs.TrackedObjectives
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class QuestDeedObserverTest {

  private val now = Instant.parse("2026-11-18T12:00:00Z")
  private val world = testWorld()
  private val questService = mockk<QuestService>(relaxed = true)

  // Runs each save at once, so the test sees what reached the service.
  private val jobs = mockk<AsyncJobExecutor> {
    val job = slot<() -> Unit>()
    every { submit(any(), capture(job)) } answers { job.captured() }
  }

  // Every actor of these tests is the master itself.
  private val masters = mockk<MasterEntityResolver> {
    every { masterEntityOf(any(), any()) } answers { secondArg() }
  }

  private val observer = QuestDeedObserver(questService, jobs, mockk(), masters, Clock.fixed(now, ZoneOffset.UTC))

  @Test
  fun `examining the wanted thing counts and is saved`() {
    val tracked = tracked(Deed.EXAMINED, EPITAPH)
    val master = masterWith(tracked)

    observer.onDeed(world, DeedFact(master, Deed.EXAMINED, EPITAPH))

    assertEquals(1, tracked.count)
    verify { questService.recordPersonal(MASTER_ID, QUEST_ID, 0, 1) }
  }

  @Test
  fun `another thing, another deed, a late or a repeated deed counts nothing`() {
    val late = tracked(Deed.EXAMINED, EPITAPH, deadline = now.minusSeconds(1))
    val tracked = tracked(Deed.EXAMINED, EPITAPH)

    observer.onDeed(world, DeedFact(masterWith(late), Deed.EXAMINED, EPITAPH))
    val master = masterWith(tracked)
    observer.onDeed(world, DeedFact(master, Deed.EXAMINED, WorldTarget.prop(78)))
    observer.onDeed(world, DeedFact(master, Deed.TALKED_TO, EPITAPH))
    assertEquals(0, late.count + tracked.count)

    repeat(2) { observer.onDeed(world, DeedFact(master, Deed.EXAMINED, EPITAPH)) }
    assertEquals(1, tracked.count)
  }

  @Test
  fun `telling a name counts only once the master knows it`() {
    val tracked = tracked(Deed.TALKED_TO, KEEPER, needsFact = NAME)
    val facts = KnownFacts()
    val master = masterWith(tracked, facts)

    observer.onDeed(world, DeedFact(master, Deed.TALKED_TO, KEEPER))
    assertEquals(0, tracked.count)

    facts.add(NAME)
    observer.onDeed(world, DeedFact(master, Deed.TALKED_TO, KEEPER))
    assertEquals(1, tracked.count)
  }

  private fun tracked(
    deed: Deed,
    target: WorldTarget,
    needsFact: FactRef? = null,
    deadline: Instant = now.plus(Duration.ofDays(1)),
  ): TrackedDeed {
    return TrackedDeed(QUEST_ID, objectiveIndex = 0, deed, target, needsFact, required = 1, deadline = deadline, count = 0)
  }

  private fun masterWith(tracked: TrackedDeed, facts: KnownFacts = KnownFacts()): EntityId {
    return world.createEntity { id ->
      add(id, ActiveQuests(MASTER_ID, TrackedObjectives(deeds = listOf(tracked))))
      add(id, facts)
    }
  }

  private companion object {
    const val MASTER_ID = 5L
    const val QUEST_ID = 70L
    val EPITAPH = WorldTarget.prop(77)
    val KEEPER = WorldTarget.townsfolk(4_000_012)
    val NAME = FactRef(FactRef.Subject.FIGURE, 7, FactRef.Aspect.NAME)
  }
}
