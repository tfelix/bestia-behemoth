package net.bestia.zone.travel.knowledge

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.travel.TravelConfig
import net.bestia.zone.travel.persistence.RouteBlockage
import net.bestia.zone.travel.persistence.RouteBlockageRepository
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteKnowledgeStoreTest {

  private val knowledge = RouteKnowledge(TravelConfig())
  private val repository: RouteBlockageRepository = mockk(relaxed = true) {
    every { save(any<RouteBlockage>()) } answers { firstArg() }
  }

  /** Runs every job at once, so a test sees the write it caused. */
  private val jobs: AsyncJobExecutor = mockk {
    val job = slot<() -> Unit>()
    every { submit(any(), capture(job)) } answers { job.captured() }
  }
  private val worldService: WorldService = mockk {
    every { record } returns mockk {
      every { shapeVersion } returns SHAPE
      every { pipelineVersion } returns PIPELINE
    }
  }

  private val store = RouteKnowledgeStore(knowledge, repository, jobs, worldService)

  @Test
  fun `a master's blockages are written and cleared`() {
    knowledge.observe(Knower.Player(1L), 7, blocked = true)
    knowledge.observe(Knower.Player(1L), 7, blocked = false)

    verify { repository.save(match { it.id == RouteBlockage.Key(1L, 7) }) }
    verify { repository.deleteById(RouteBlockage.Key(1L, 7)) }
  }

  @Test
  fun `a creature's are never written`() {
    knowledge.observe(Knower.Creature(99L), 7, blocked = true)

    verify(exactly = 0) { repository.save(any()) }
  }

  @Test
  fun `loading keeps this world's fresh rows and drops the rest`() {
    val fresh = row(edge = 1, shape = SHAPE, lapsesAt = Instant.now().plus(Duration.ofDays(3)))
    val otherWorld = row(edge = 2, shape = SHAPE + 1, lapsesAt = Instant.now().plus(Duration.ofDays(3)))
    val lapsed = row(edge = 3, shape = SHAPE, lapsesAt = Instant.now().minus(Duration.ofDays(1)))
    every { repository.findAll() } returns listOf(fresh, otherWorld, lapsed)

    store.loadAll()

    assertTrue(knowledge.believesBlocked(Knower.Player(1L), 1))
    assertFalse(knowledge.believesBlocked(Knower.Player(1L), 2))
    verify { repository.deleteAll(listOf(otherWorld, lapsed)) }
  }

  private fun row(edge: Int, shape: Long, lapsesAt: Instant): RouteBlockage {
    return RouteBlockage(RouteBlockage.Key(1L, edge), lapsesAt, shape, PIPELINE)
  }

  private companion object {
    const val SHAPE = 5L
    const val PIPELINE = 9L
  }
}
