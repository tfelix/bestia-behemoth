package net.bestia.zone.ai.rumour

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ai.knowledge.KnowledgeService
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That news stops being news, and that the ledger stops holding it.
 *
 * The sweep is the half with no visible symptom. A conversation filters spent news out on the way past,
 * so a sweep that never ran would look completely correct to a player while the table grew without
 * bound - the kind of thing found a year later rather than in a test.
 */
class RumourServiceTest {

  private val repository = mockk<RumourRepository>(relaxed = true).also {
    // `save` is generic, so a relaxed mock hands back an Object the call site cannot cast.
    every { it.save(any<Rumour>()) } answers { firstArg() }
  }
  private val knowledge = mockk<KnowledgeService>(relaxed = true)
  private val clock = mockk<BestiaClock>()
  private val worldService = mockk<WorldService>(relaxed = true)

  private val async = mockk<AsyncJobExecutor>().also {
    // Inline, so a write is observable in the same breath as the call that caused it.
    every { it.submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }

  private val registry = RumourRegistry(repository, async, worldService)
  private val sut = RumourService(registry, knowledge, worldService, clock)

  @Test
  fun `spent news is dropped and current news is kept`() {
    onDay(100.0)
    registry.add(TOWN, RumourKind.BOSS_SLAIN, "", 80.0, expiresOnDay = 90.0, strength = 1.0)
    registry.add(TOWN, RumourKind.BOSS_SLAIN, "", 95.0, expiresOnDay = 130.0, strength = 1.0)

    assertEquals(2, registry.size)

    sut.forgetExpired()

    assertEquals(1, registry.size, "the expired row should be gone and the current one kept")
    assertTrue(registry.heardBy(TOWN).single().expiresOnDay == 130.0)
  }

  @Test
  fun `a swept town forgets what it knew, so the news stops being offered`() {
    onDay(100.0)
    registry.add(TOWN, RumourKind.BOSS_SLAIN, "", 80.0, expiresOnDay = 90.0, strength = 1.0)

    sut.forgetExpired()

    verify { knowledge.forget(TOWN) }
  }

  @Test
  fun `a sweep with nothing to do touches nothing`() {
    onDay(100.0)
    registry.add(TOWN, RumourKind.BOSS_SLAIN, "", 95.0, expiresOnDay = 130.0, strength = 1.0)

    sut.forgetExpired()

    assertEquals(1, registry.size)
    // The cache is expensive to rebuild, and a sweep that invalidated every town holding any news at
    // all would do it every three minutes for as long as the news lasted.
    verify(exactly = 0) { knowledge.forget(any()) }
  }

  @Test
  fun `a town whose last news expires drops out of the registry entirely`() {
    onDay(100.0)
    registry.add(TOWN, RumourKind.BOSS_SLAIN, "", 80.0, expiresOnDay = 90.0, strength = 1.0)

    sut.forgetExpired()

    assertTrue(registry.settlementsWithNews().isEmpty(), "an empty town should not keep an empty list")
  }

  private fun onDay(day: Double) {
    every { clock.now() } returns mockk(relaxed = true) { every { absoluteDay } returns day }
  }

  private companion object {
    const val TOWN = 3
  }
}
