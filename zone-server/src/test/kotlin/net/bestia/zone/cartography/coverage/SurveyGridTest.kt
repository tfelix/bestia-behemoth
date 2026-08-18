package net.bestia.zone.cartography.coverage

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.core.WorldWrap
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurveyGridTest {

  private val wrapping = SurveyGrid(WorldWrap(world(wrapX = true, wrapY = true)))
  private val flat = SurveyGrid(WorldWrap(world(wrapX = false, wrapY = false)))

  @Test
  fun `the grid covers the world at the cell size`() {
    assertEquals(64.0, SurveyGrid.CELL_METRES)
    assertEquals(2000, wrapping.cellsAcross)
    assertEquals(2000, wrapping.cellsDown)
  }

  @Test
  fun `a cell contains the positions that map into it`() {
    for (worldX in doubleArrayOf(0.0, 1.0, 63.999, 64.0, 12_345.6, 127_999.9)) {
      val cell = wrapping.cellXOf(worldX)

      assertTrue(
        worldX >= wrapping.cellMinX(cell) && worldX < wrapping.cellMinX(cell + 1),
        "$worldX is not inside cell $cell, which is ${wrapping.cellMinX(cell)}..${wrapping.cellMinX(cell + 1)}"
      )
    }
  }

  @Test
  fun `positions outside a wrapped world fold back into it`() {
    assertEquals(wrapping.cellXOf(100.0), wrapping.cellXOf(128_000.0 + 100.0))
    assertEquals(wrapping.cellXOf(100.0), wrapping.cellXOf(-128_000.0 + 100.0))

    // One metre west of the origin is the far eastern cell, not cell -1 and not cell 0.
    assertEquals(wrapping.cellsAcross - 1, wrapping.cellXOf(-1.0))
  }

  @Test
  fun `an axis that does not wrap has cells outside the world instead`() {
    assertFalse(flat.holdsX(-1))
    assertFalse(flat.holdsX(flat.cellsAcross))
    assertTrue(flat.holdsX(0))

    // Nothing folds, so the raw index is the index.
    assertEquals(-1, flat.normaliseX(-1))
  }

  @Test
  fun `the last cell of a span stops short of the boundary it ends on`() {
    // A tile's east edge is its neighbour's west edge. Both claiming the cell there would double-count it.
    assertEquals(0, wrapping.rawCellXBefore(64.0))
    assertEquals(1, wrapping.rawCellXBefore(128.0))
    assertEquals(1, wrapping.rawCellXBefore(127.9))

    // Which is one less than the cell the same coordinate falls *in*, exactly on a boundary and not otherwise.
    assertEquals(1, wrapping.rawCellXOf(64.0))
    assertEquals(1, wrapping.rawCellXOf(127.9))
  }

  @Test
  fun `a range inside the world folds to itself`() {
    assertEquals(listOf(10..20), wrapping.foldX(10, 20))
  }

  @Test
  fun `a range across the seam folds into two, together holding every cell once`() {
    val folded = wrapping.foldX(-3, 4)

    assertEquals(2, folded.size)
    assertEquals(8, folded.sumOf { it.count() }, "the eight cells of -3..4 must survive the fold")
    assertEquals(listOf(1997..1999, 0..4), folded)
  }

  @Test
  fun `a range at least as wide as the world folds to the world once`() {
    // A level-9 tile is wider than a 128 km world. Folding it into overlapping pieces would let a caller count
    // the same cell twice and conclude an area was more charted than it is.
    val folded = wrapping.foldX(-100, wrapping.cellsAcross + 100)

    assertEquals(listOf(0 until wrapping.cellsAcross), folded)
    assertEquals(wrapping.cellsAcross, folded.sumOf { it.count() })
  }

  @Test
  fun `folding an unwrapped axis clips instead of wrapping`() {
    assertEquals(listOf(0..20), flat.foldX(-30, 20))
    assertEquals(emptyList<IntRange>(), flat.foldX(-30, -5))
  }

  private fun world(wrapX: Boolean, wrapY: Boolean) = WorldConfig(
    seed = 1L,
    widthCells = 128,
    heightCells = 128,
    wrapX = wrapX,
    wrapY = wrapY
  )
}
