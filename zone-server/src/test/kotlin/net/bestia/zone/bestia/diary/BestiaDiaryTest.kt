package net.bestia.zone.bestia.diary

import net.bestia.zone.bestia.persistence.DiaryLineRepository
import net.bestia.zone.bestia.persistence.DiaryLine
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.persistence.AsyncJobExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class BestiaDiaryTest {

  private val now = Instant.parse("2026-11-11T12:00:00Z")
  private val lines = mockk<DiaryLineRepository>(relaxed = true) {
    every { save(any<DiaryLine>()) } answers { firstArg() }
  }
  private val jobs = mockk<AsyncJobExecutor> {
    every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }
  private val diary = BestiaDiary(lines, jobs, DiaryConfig(keepDays = 7, maxLinesPerBestia = 3), Clock.fixed(now, ZoneOffset.UTC))

  @Test
  fun `a happening becomes a line with its goods`() {
    val saved = slot<DiaryLine>()
    every { lines.save(capture(saved)) } answers { saved.captured }

    diary.onHappened(BestiaHappened(this, 7L, 1L, BestiaHappened.Kind.HAULED, items = mapOf("log" to 12, "herb" to 3)))

    assertEquals(mapOf("log" to 12, "herb" to 3), saved.captured.itemAmounts())
    assertEquals(now, saved.captured.at)
  }

  @Test
  fun `a bestia keeps only its newest lines`() {
    val newestFirst = List(5) { DiaryLine(7L, 1L, now.minusSeconds(it.toLong()), BestiaHappened.Kind.HAULED) }
    every { lines.countByPlayerBestiaId(1L) } returns 5
    every { lines.findAllByPlayerBestiaIdOrderByAtDesc(1L) } returns newestFirst

    diary.record(newestFirst.first())

    verify { lines.deleteAll(newestFirst.takeLast(2)) }
  }

  @Test
  fun `lines older than the kept days are dropped`() {
    diary.dropOldLines()

    verify { lines.deleteOlderThan(now - Duration.ofDays(7)) }
  }
}
