package net.bestia.zone.world.ground

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColumnLevelsTest {

  private val size = 32

  @Test
  fun `a fresh column is unmarked`() {
    val levels = ColumnLevels(size)

    assertTrue(levels.isEmpty)
    assertEquals(0, levels.markedCells)
    assertEquals(0, levels[3, 4])
  }

  @Test
  fun `a cell remembers what was added to it`() {
    val levels = ColumnLevels(size)

    levels.add(3, 4, 40)
    levels.add(3, 4, 20)

    assertEquals(60, levels[3, 4])
    assertEquals(1, levels.markedCells)
  }

  @Test
  fun `wear saturates rather than wrapping`() {
    val levels = ColumnLevels(size)

    repeat(10) { levels.add(0, 0, 100) }

    assertEquals(255, levels[0, 0])
  }

  @Test
  fun `adding reports only a change the client would draw`() {
    val levels = ColumnLevels(size)

    assertTrue(levels.add(0, 0, 16), "crossing into the first drawn level is worth announcing")
    assertFalse(levels.add(0, 0, 1), "a step that does not move the nibble is not")
    assertTrue(levels.add(0, 0, 15), "crossing into the next one is")
  }

  @Test
  fun `cells are indexed the way ColumnMask indexes them`() {
    val levels = ColumnLevels(size)

    assertEquals(0, levels.indexOf(0, 0))
    assertEquals(1, levels.indexOf(1, 0))
    assertEquals(size, levels.indexOf(0, 1))
    assertEquals(ColumnMask(size).indexOf(5, 7), levels.indexOf(5, 7))
  }

  @Test
  fun `decay takes the same amount off every cell`() {
    val levels = ColumnLevels(size)
    levels.add(0, 0, 200)
    levels.add(1, 0, 50)

    levels.decayBy(30)

    assertEquals(170, levels[0, 0])
    assertEquals(20, levels[1, 0])
  }

  @Test
  fun `a well used cell outlives a lightly used one`() {
    val levels = ColumnLevels(size)
    levels.add(0, 0, 200)
    levels.add(1, 0, 50)

    repeat(4) { levels.decayBy(30) }

    assertTrue(levels[0, 0] > 0, "the walked line is still there")
    assertEquals(0, levels[1, 0], "the ground beside it has closed over")
  }

  @Test
  fun `decay clamps at zero and forgets the cell`() {
    val levels = ColumnLevels(size)
    levels.add(0, 0, 10)

    levels.decayBy(99)

    assertEquals(0, levels[0, 0])
    assertEquals(0, levels.markedCells)
    assertTrue(levels.isEmpty)
  }

  @Test
  fun `decaying nothing is not worth a redraw`() {
    val levels = ColumnLevels(size)

    assertFalse(levels.decayBy(10))
  }

  @Test
  fun `the wire form is two cells to a byte, low nibble first`() {
    val levels = ColumnLevels(size)
    levels.add(0, 0, 0x10)
    levels.add(1, 0, 0xF0)

    val packed = levels.toNibbles()

    assertEquals(ColumnLevels.nibbleLength(size), packed.size)
    assertEquals(0xF1, packed[0].toInt() and 0xFF)
  }

  @Test
  fun `the wire form is half the stored form`() {
    assertEquals(size * size / 2, ColumnLevels.nibbleLength(size))
  }

  @Test
  fun `a column survives storage`() {
    val levels = ColumnLevels(size)
    levels.add(0, 0, 200)
    levels.add(31, 31, 7)

    val back = ColumnLevels.fromBytes(size, levels.toBytes())

    assertContentEquals(levels.toBytes(), back.toBytes())
    assertEquals(200, back[0, 0])
    assertEquals(7, back[31, 31])
    assertEquals(2, back.markedCells)
  }

  @Test
  fun `a column of the wrong width is refused rather than read short`() {
    assertFailsWith<IllegalArgumentException> { ColumnLevels.fromBytes(size, ByteArray(10)) }
  }

  @Test
  fun `every marked cell is visited, in index order`() {
    val levels = ColumnLevels(size)
    levels.add(1, 0, 10)
    levels.add(0, 1, 20)

    val seen = mutableListOf<Triple<Int, Int, Int>>()
    levels.forEachMarked { x, y, level -> seen += Triple(x, y, level) }

    assertEquals(listOf(Triple(1, 0, 10), Triple(0, 1, 20)), seen)
  }
}
