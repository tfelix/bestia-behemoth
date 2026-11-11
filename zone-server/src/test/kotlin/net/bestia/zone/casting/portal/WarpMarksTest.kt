package net.bestia.zone.casting.portal

import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WarpMarksTest {

  @Test
  fun `a full list makes room by dropping the oldest spot`() {
    val marks = WarpMarks()
    (1..4).forEach { marks.mark(Vec3L(it.toLong(), 0, 0), capacity = 3) }

    assertEquals(listOf(2L, 3L, 4L), marks.spots.map { it.x })
  }

  @Test
  fun `spots survive their column`() {
    val spots = listOf(Vec3L(1, -2, 3), Vec3L(40_000, 7, -5))

    assertEquals(spots, WarpMarks.decode(WarpMarks.encode(spots)))
    assertEquals(emptyList<Vec3L>(), WarpMarks.decode(""))
  }
}
