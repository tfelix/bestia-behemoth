package net.bestia.zone.questgen

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.EventKind
import net.bestia.zone.chronicle.LivingChronicle
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.observe.DeedFact
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.questgen.ending.RelicFinds
import net.bestia.zone.questgen.plan.GeneratedQuestWorld
import org.junit.jupiter.api.Test

class RelicFindsTest {

  private val world = testWorld()
  private val chronicle = mockk<LivingChronicle>(relaxed = true)
  private val questWorld = mockk<GeneratedQuestWorld> {
    every { artifactIn(any()) } returns null
    every { artifactIn(RELIC_CACHE) } returns BLADE
  }

  // Runs each job at once, so the test sees what it wrote.
  private val jobs = mockk<AsyncJobExecutor> {
    val job = slot<() -> Unit>()
    every { submit(any(), capture(job)) } answers { job.captured() }
  }

  // The digger of these tests is the master itself.
  private val masters = mockk<MasterEntityResolver> { every { masterEntityOf(any(), any()) } answers { secondArg() } }

  private val finds = RelicFinds(questWorld, chronicle, jobs, mockk(), masters)

  private val digger = world.createEntity { id -> add(id, Master(masterId = 9, name = "Ayla")) }

  @Test
  fun `digging up an artifact goes into the history with the finder's name`() {
    finds.onDeed(world, DeedFact(digger, DeedFact.Deed.EXAMINED, WorldTarget.cache(RELIC_CACHE)))

    verify { chronicle.record(EventKind.ARTIFACT_TAKEN, listOf(Actor(ActorType.ARTIFACT, BLADE)), null, LivingChronicle.Doer(9, "Ayla")) }
  }

  @Test
  fun `a cache without an artifact and a grave are no finds`() {
    finds.onDeed(world, DeedFact(digger, DeedFact.Deed.EXAMINED, WorldTarget.cache(77)))
    finds.onDeed(world, DeedFact(digger, DeedFact.Deed.EXAMINED, WorldTarget.grave(3)))

    // Not `record(any(), ...)`: a matcher on the place would make MockK rewrite Vec2d for the whole test JVM.
    verify { chronicle wasNot Called }
  }

  private companion object {
    const val RELIC_CACHE = 55L
    const val BLADE = 3
  }
}
