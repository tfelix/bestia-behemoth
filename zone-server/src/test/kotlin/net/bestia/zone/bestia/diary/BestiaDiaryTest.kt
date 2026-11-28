package net.bestia.zone.bestia.diary

import net.bestia.zone.bestia.persistence.DiaryLineRepository
import net.bestia.zone.bestia.persistence.DiaryLine
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.persistence.AsyncJobExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class BestiaDiaryTest {

  private val now = Instant.parse("2026-11-11T12:00:00Z")
  private val lines = mockk<DiaryLineRepository>(relaxed = true) {
    every { save(any<DiaryLine>()) } answers { firstArg() }
    every { findFirstByPlayerBestiaIdAndKindOrderByAtDesc(any(), any()) } returns null
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
  fun `a haul within the hour adds its goods to the last haul's line`() {
    val last = DiaryLine(7L, 1L, now.minusSeconds(50 * 60), BestiaHappened.Kind.HAULED, items = "log:12", place = "Camp")
    every { lines.findFirstByPlayerBestiaIdAndKindOrderByAtDesc(1L, BestiaHappened.Kind.HAULED) } returns last

    diary.record(DiaryLine(7L, 1L, now, BestiaHappened.Kind.HAULED, items = "log:3,herb:2", place = "Camp"))

    assertEquals(mapOf("log" to 15, "herb" to 2), last.itemAmounts())
    assertEquals(now.minusSeconds(50 * 60), last.at)
    verify(exactly = 1) { lines.save(any<DiaryLine>()) }
    verify { lines.save(last) }
  }

  @Test
  fun `a haul after the hour or to another chest starts a new line`() {
    val older = DiaryLine(7L, 1L, now.minusSeconds(61 * 60), BestiaHappened.Kind.HAULED, items = "log:12", place = "Camp")
    every { lines.findFirstByPlayerBestiaIdAndKindOrderByAtDesc(1L, BestiaHappened.Kind.HAULED) } returns older
    val late = DiaryLine(7L, 1L, now, BestiaHappened.Kind.HAULED, items = "log:3", place = "Camp")
    val elsewhere = DiaryLine(7L, 1L, now, BestiaHappened.Kind.HAULED, items = "log:3", place = null)

    diary.record(late)
    every { lines.findFirstByPlayerBestiaIdAndKindOrderByAtDesc(1L, BestiaHappened.Kind.HAULED) } returns
      DiaryLine(7L, 1L, now.minusSeconds(60), BestiaHappened.Kind.HAULED, items = "log:1", place = "Camp")
    diary.record(elsewhere)

    assertEquals(mapOf("log" to 12), older.itemAmounts())
    verify { lines.save(late) }
    verify { lines.save(elsewhere) }
  }

  @Test
  fun `only kinds that merge look for a line to merge into`() {
    diary.record(DiaryLine(7L, 1L, now, BestiaHappened.Kind.ATTACKED))

    verify(exactly = 0) { lines.findFirstByPlayerBestiaIdAndKindOrderByAtDesc(any(), any()) }
    assertNull(BestiaHappened.Kind.ATTACKED.mergesWithin)
  }

  @Test
  fun `lines older than the kept days are dropped`() {
    diary.dropOldLines()

    verify { lines.deleteOlderThan(now - Duration.ofDays(7)) }
  }
}
