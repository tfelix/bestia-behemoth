package net.bestia.zone.world.ground

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WornColumnTest {

  private val size = 32

  /** Three Bestia days, the shipped default, so these numbers are the ones a player would actually see. */
  private val fadeSeconds = 259_200L

  private fun worn(level: Int, at: Long = 0): WornColumn {
    val levels = ColumnLevels(size)
    levels.add(0, 0, level)
    return WornColumn(levels, at)
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

  @Test
  fun `a busy line outlives the ground beside it`() {
    val levels = ColumnLevels(size)
    levels.add(0, 0, ColumnLevels.MAX_LEVEL)
    levels.add(1, 0, 40)
    val column = WornColumn(levels, 0)

    column.ageTo(fadeSeconds / 2, fadeSeconds)

    assertTrue(column.levels[0, 0] > 0, "the walked line is still there")
    assertEquals(0, column.levels[1, 0], "the ground beside it has closed over")
  }
}
