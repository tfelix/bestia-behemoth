package net.bestia.zone.ground

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkedColumnTest {

  private val size = 32

  /** Three Bestia days, the shipped default, so these numbers are the ones a player would actually see. */
  private val fadeSeconds = 259_200L

  private fun worn(level: Int, at: Long = 0): MarkedColumn {
    val levels = ColumnLevels(size)
    levels.add(0, 0, level)
    return MarkedColumn(levels, at)
  }

  @Test
  fun `a path fades to nothing over the fade duration`() {
    val column = worn(ColumnLevels.MAX_LEVEL)

    column.ageTo(fadeSeconds, fadeSeconds)

    assertEquals(0, column.levels[0, 0])
    assertTrue(column.isEmpty)
  }

  @Test
  fun `half the fade takes about half the level`() {
    val column = worn(ColumnLevels.MAX_LEVEL)

    column.ageTo(fadeSeconds / 2, fadeSeconds)

    assertEquals(128, column.levels[0, 0])
  }

  @Test
  fun `a sweep too short to move a level still fades eventually`() {
    val column = worn(ColumnLevels.MAX_LEVEL)

    // A minute against a three day fade is a quarter of a level - nothing, as an integer. Swept a minute at a
    // time against the real clock it must still arrive, or every path in the world is permanent.
    //
    // The clock is absolute, as the sweep's own is. That is the whole mechanism: `lastDecayedSecond` does not
    // move on a pass that removed nothing, so the minutes pile up until they are worth a level.
    var now = 0L
    repeat((fadeSeconds / 60).toInt()) {
      now += 60
      column.ageTo(now, fadeSeconds)
    }

    assertEquals(0, column.levels[0, 0], "the remainder was dropped each pass")
  }

  @Test
  fun `aging twice to the same moment changes nothing`() {
    val column = worn(200)

    column.ageTo(fadeSeconds / 4, fadeSeconds)
    val once = column.levels[0, 0]
    assertFalse(column.ageTo(fadeSeconds / 4, fadeSeconds))

    assertEquals(once, column.levels[0, 0])
  }

  @Test
  fun `a clock that has not moved fades nothing`() {
    val column = worn(200, at = 5_000)

    assertFalse(column.ageTo(5_000, fadeSeconds))
    assertEquals(200, column.levels[0, 0])
  }

  @Test
  fun `fading marks the column for writing`() {
    val column = worn(ColumnLevels.MAX_LEVEL)
    assertFalse(column.dirty)

    column.ageTo(fadeSeconds / 2, fadeSeconds)

    assertTrue(column.dirty)
  }

  private fun full(at: Long = 0): MarkedColumn {
    val levels = ColumnLevels(size)
    for (index in 0 until size * size) levels.add(index, ColumnLevels.MAX_LEVEL)
    return MarkedColumn(levels, at)
  }

  private fun MarkedColumn.everyCell(): Set<Int> {
    return (0 until size * size).map { levels[it] }.toSet()
  }

  /**
   * **Cross-language fixture.** A client continues the fade from `lastDecayedSecond` with this same integer
   * arithmetic, so the steps taken, the remainder kept and the clock it leaves behind are all pinned here from
   * literals and mirrored in the client's `ClientColumnLevelsTest`.
   */
  @Test
  fun `a short wait removes nothing and does not move the clock`() {
    val column = full()

    assertFalse(column.ageTo(180, fadeSeconds))

    assertEquals(0L, column.lastDecayedSecond)
    assertEquals(setOf(255), column.everyCell())
  }

  @Test
  fun `half the fade takes 127 levels and leaves the clock at 129091`() {
    val column = full()

    column.ageTo(129_600, fadeSeconds)

    assertEquals(129_091L, column.lastDecayedSecond)
    assertEquals(setOf(128), column.everyCell())
  }

  @Test
  fun `the seconds a level did not pay for are kept for the next pass`() {
    val column = full()

    column.ageTo(1_017, fadeSeconds)
    assertEquals(1_016L, column.lastDecayedSecond)
    assertEquals(setOf(254), column.everyCell())

    column.ageTo(2_032, fadeSeconds)
    assertEquals(1_016L, column.lastDecayedSecond, "a second short of a level moved the clock")
    assertEquals(setOf(254), column.everyCell())

    column.ageTo(2_033, fadeSeconds)
    assertEquals(2_032L, column.lastDecayedSecond)
    assertEquals(setOf(253), column.everyCell())
  }

  @Test
  fun `a busy line outlives the ground beside it`() {
    val levels = ColumnLevels(size)
    levels.add(0, 0, ColumnLevels.MAX_LEVEL)
    levels.add(1, 0, 40)
    val column = MarkedColumn(levels, 0)

    column.ageTo(fadeSeconds / 2, fadeSeconds)

    assertTrue(column.levels[0, 0] > 0, "the walked line is still there")
    assertEquals(0, column.levels[1, 0], "the ground beside it has closed over")
  }
}
