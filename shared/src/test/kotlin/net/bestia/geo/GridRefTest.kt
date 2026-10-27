package net.bestia.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class GridRefTest {

  private val grid = GridRef(128_000.0, 128_000.0)

  @Test
  fun `every shared case holds`() {
    var checked = 0

    for (line in cases()) {
      val cells = line.split('\t')
      when (cells[0]) {
        "format" -> assertEquals(cells[4], grid.format(cells[1].toDouble(), cells[2].toDouble(), cells[3].toInt()), line)
        "parse" -> {
          val point = assertNotNull(grid.parse(cells[1]), line)
          assertEquals(cells[2].toDouble(), point.eastMetres, 1e-9, line)
          assertEquals(cells[3].toDouble(), point.northMetres, 1e-9, line)
          assertEquals(cells[4].toDouble(), point.precisionMetres, 1e-9, line)
        }
        "invalid" -> assertNull(grid.parse(cells.getOrElse(1) { "" }), line)
        "column" -> {
          assertEquals(cells[2], GridRef.columnName(cells[1].toInt()), line)
          assertEquals(cells[1].toInt(), GridRef.columnIndex(cells[2]), line)
        }
        else -> fail("Unknown case kind in '$line'")
      }
      checked++
    }

    assertEquals(true, checked > 20, "only $checked cases were read")
  }

  @Test
  fun `a formatted reference parses back into the cell it came from`() {
    for (digits in 1..GridRef.MAX_DIGITS) {
      val ref = grid.format(61_312.7, 91_725.2, digits)
      val point = assertNotNull(grid.parse(ref), ref)

      assertEquals(true, 61_312.7 >= point.eastMetres - point.precisionMetres / 2, ref)
      assertEquals(true, 61_312.7 < point.eastMetres + point.precisionMetres / 2, ref)
      assertEquals(true, 91_725.2 >= point.northMetres - point.precisionMetres / 2, ref)
      assertEquals(true, 91_725.2 < point.northMetres + point.precisionMetres / 2, ref)
    }
  }

  private fun cases(): List<String> {
    val text = javaClass.getResource("/gridref-cases.tsv")?.readText() ?: fail("gridref-cases.tsv is missing")
    return text.lines().filter { it.isNotEmpty() && !it.startsWith("#") }
  }
}
