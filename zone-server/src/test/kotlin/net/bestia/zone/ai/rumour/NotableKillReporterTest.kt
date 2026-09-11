package net.bestia.zone.ai.rumour

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.BestiaRepository
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertTrue

/**
 * That the threshold exists and means something.
 *
 * Without one, every rat killed outside a village is news: the ledger fills with rows nobody would ever
 * mention, and the single-holder memory a player was meant to go looking for is buried under vermin.
 * This is the only thing standing between the feature and that, so it is worth a test of its own.
 */
class NotableKillReporterTest {

  private val rumours = mockk<RumourService>(relaxed = true)
  private val repository = mockk<BestiaRepository>()

  private val sut = NotableKillReporter(rumours, repository)

  @Test
  fun `vermin is not news`() {
    given(level = 3)

    sut.report(SPECIES, 100, 200)

    verify(exactly = 0) { rumours.post(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `something large is news`() {
    given(level = 60)

    sut.report(SPECIES, 100, 200)

    verify(exactly = 1) { rumours.post(RumourKind.BOSS_SLAIN, 100, 200, any(), any()) }
  }

  @Test
  fun `a bigger beast is heard further`() {
    val strengths = mutableListOf<Double>()

    listOf(25, 50, 80).forEach { level ->
      given(level)
      val captured = slot<Double>()
      every { rumours.post(any(), any(), any(), capture(captured), any()) } returns emptyList()

      sut.report(SPECIES, 0, 0)
      strengths += captured.captured
    }

    assertTrue(
      strengths == strengths.sorted() && strengths.first() < strengths.last(),
      "strength should rise with level, got $strengths"
    )
    assertTrue(strengths.last() <= 1.0, "strength is a fraction, got ${strengths.last()}")
  }

  /** A species row that has gone since the entity spawned is a missing row, not a level-zero beast. */
  @Test
  fun `an unknown species is not news`() {
    every { repository.findById(SPECIES) } returns Optional.empty()

    sut.report(SPECIES, 100, 200)

    verify(exactly = 0) { rumours.post(any(), any(), any(), any(), any()) }
  }

  private fun given(level: Int) {
    val bestia = Bestia(
      id = SPECIES,
      identifier = "test_beast",
      level = level,
      experienceReward = 10,
      health = 100,
      mana = 10,
    )

    every { repository.findById(SPECIES) } returns Optional.of(bestia)
  }

  private companion object {
    const val SPECIES = 7L
  }
}
