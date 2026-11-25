package net.bestia.zone.worldevent.alpha

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.worldevent.persistence.HuntedDen
import net.bestia.zone.worldevent.persistence.HuntedDenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DenHuntingTest {

  private val saved = slot<Iterable<HuntedDen>>()
  private val repository = mockk<HuntedDenRepository> {
    every { findAll() } returns listOf(HuntedDen(1, 100))
    every { saveAll(capture(saved)) } answers { firstArg<Iterable<HuntedDen>>().toList() }
  }
  private val sut = DenHunting(repository)

  @Test
  fun `a stored den counts from its last hunt, a new one from when it was first asked about`() {
    assertEquals(mapOf(1L to 900L, 2L to 0L), sut.unhuntedSeconds(listOf(1, 2), now = 1_000))
    assertEquals(mapOf(1L to 1_900L, 2L to 1_000L), sut.unhuntedSeconds(listOf(1, 2), now = 2_000))
  }

  @Test
  fun `a hunt starts the count again and is written out`() {
    sut.hunted(1, atSecond = 800)

    assertEquals(mapOf(1L to 200L), sut.unhuntedSeconds(listOf(1), now = 1_000))
    sut.flush()
    assertEquals(listOf(1L to 800L), saved.captured.map { it.denFeatureId to it.lastHuntedSecond })
  }

  @Test
  fun `nothing to write, nothing written`() {
    sut.flush()

    verify(exactly = 0) { repository.saveAll(any<Iterable<HuntedDen>>()) }
  }
}
