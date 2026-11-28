package net.bestia.zone.bestia.diary

import net.bestia.zone.bestia.diary.AwayReport.Blocker
import net.bestia.zone.bestia.diary.AwayReport.Hint
import net.bestia.zone.bestia.diary.BestiaHappened.Kind
import net.bestia.zone.bestia.persistence.DiaryLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AwayReportTest {

  private val loggedOut = Instant.parse("2026-11-27T22:00:00Z")
  private val now = Instant.parse("2026-11-28T08:00:00Z")
  private val pip = AwayReport.Subject(PIP, "Pip", "fox", now = "RESTING_AT_HOME", state = BestiaState.RESTING)

  private fun at(hour: Int, minute: Int = 0): Instant {
    return loggedOut.plusSeconds(hour * 3600L + minute * 60L)
  }

  private fun line(at: Instant, kind: Kind, items: String? = null, place: String? = null, reason: String? = null): DiaryLine {
    return DiaryLine(MASTER, PIP, at, kind, items, place, reason)
  }

  private fun state(at: Instant, state: BestiaState): DiaryLine {
    return line(at, Kind.STATE, reason = state.name)
  }

  private fun report(lines: List<DiaryLine>, subject: AwayReport.Subject = pip): AwayReport {
    return AwayReport.of(subject, lines, since = loggedOut, until = now)
  }

  @Test
  fun `a night of gathering until the food ran out`() {
    val lines = listOf(
      state(loggedOut.minusSeconds(3600), BestiaState.WORKING),
      line(loggedOut.minusSeconds(600), Kind.HAULED, items = "log:50", place = "Camp chest"),
      line(at(1), Kind.HAULED, items = "log:120,resin:20", place = "Camp chest"),
      line(at(2, 10), Kind.ATTACKED),
      line(at(4), Kind.HAULED, items = "log:94,resin:16", place = "Camp chest"),
      line(at(7), Kind.OUT_OF_FOOD),
      state(at(7, 1), BestiaState.RESTING),
    )

    val report = report(lines)

    assertEquals(mapOf("log" to 214, "resin" to 36), report.brought)
    assertEquals(listOf("Camp chest"), report.broughtTo)
    assertEquals(mapOf(BestiaState.WORKING to 7 * 3600L + 60, BestiaState.RESTING to 2 * 3600L + 59 * 60), report.timeIn)
    assertEquals(listOf(Kind.ATTACKED, Kind.OUT_OF_FOOD), report.incidents.map { it.kind })
    assertEquals("RESTING_AT_HOME", report.now)
    assertEquals(Blocker.OUT_OF_FOOD, report.blocker)
    assertEquals(Hint.BRING_FOOD, report.hint)
    assertTrue(report.isEventful)
  }

  @Test
  fun `a stop it has worked on from is no blocker`() {
    val lines = listOf(
      line(at(1), Kind.WORK_PAUSED, reason = "CHEST_FULL"),
      state(at(1, 1), BestiaState.PAUSED),
      state(at(3), BestiaState.WORKING),
    )

    val report = report(lines, pip.copy(now = "IDLE", state = BestiaState.IDLE))

    assertNull(report.blocker)
    assertEquals(listOf(Kind.WORK_PAUSED), report.incidents.map { it.kind })
  }

  @Test
  fun `a stop from before the master left still blocks`() {
    val lines = listOf(
      line(loggedOut.minusSeconds(7200), Kind.WORK_PAUSED, place = "CAMPFIRE", reason = "STATION_OUT"),
      state(loggedOut.minusSeconds(7000), BestiaState.PAUSED),
    )

    val report = report(lines, pip.copy(now = "CRAFTING", nowAt = "CAMPFIRE", state = BestiaState.PAUSED))

    assertEquals(Blocker.STATION_OUT, report.blocker)
    assertEquals(Hint.FEED_FIRE, report.hint)
    assertTrue(report.incidents.isEmpty())
    assertEquals(mapOf(BestiaState.PAUSED to 10 * 3600L), report.timeIn)
  }

  @Test
  fun `a bestia at work now has no blocker whatever the diary says`() {
    val lines = listOf(line(at(1), Kind.WORK_PAUSED, reason = "NO_ORE"))

    assertNull(report(lines, pip.copy(now = "MINING", state = BestiaState.WORKING)).blocker)
  }

  @Test
  fun `a dead bestia waits to be revived and its time stops with its death`() {
    val lines = listOf(
      state(at(0), BestiaState.WORKING),
      line(at(3), Kind.ATTACKED),
      line(at(3, 5), Kind.DIED),
    )

    val report = report(lines, pip.copy(now = AwayReport.DEAD, state = null))

    assertEquals(Blocker.DIED, report.blocker)
    assertEquals(Hint.REVIVE, report.hint)
    assertEquals(mapOf(BestiaState.WORKING to 3 * 3600L + 300), report.timeIn)
  }

  @Test
  fun `a bestia not in the world counts no time past its last line`() {
    val lines = listOf(state(at(1), BestiaState.WORKING), state(at(2), BestiaState.RESTING))

    val report = report(lines, pip.copy(now = AwayReport.NOT_IN_WORLD, state = null))

    assertEquals(mapOf(BestiaState.WORKING to 3600L), report.timeIn)
  }

  @Test
  fun `finished queues are counted per station`() {
    val lines = listOf(
      line(at(1), Kind.QUEUE_FINISHED, place = "WORKBENCH"),
      line(at(2), Kind.QUEUE_FINISHED, place = "WORKBENCH"),
      line(at(3), Kind.QUEUE_FINISHED, place = "CAMPFIRE"),
    )

    assertEquals(mapOf("WORKBENCH" to 2, "CAMPFIRE" to 1), report(lines).madeAt)
  }

  @Test
  fun `only the newest incidents are kept and other bestias' lines are ignored`() {
    val attacks = List(30) { line(at(0, it), Kind.ATTACKED) }
    val other = DiaryLine(MASTER, OTHER, at(1), Kind.HAULED, items = "log:5")

    val report = report(attacks + other)

    assertEquals(AwayReport.MAX_INCIDENTS, report.incidents.size)
    assertEquals(at(0, 29), report.incidents.last().at)
    assertTrue(report.brought.isEmpty())
  }

  @Test
  fun `a quiet night is not news`() {
    val report = report(listOf(state(at(0), BestiaState.RESTING)))

    assertFalse(report.isEventful)
    assertEquals(mapOf(BestiaState.RESTING to 10 * 3600L), report.timeIn)
  }

  private companion object {
    const val MASTER = 7L
    const val PIP = 1L
    const val OTHER = 2L
  }
}
