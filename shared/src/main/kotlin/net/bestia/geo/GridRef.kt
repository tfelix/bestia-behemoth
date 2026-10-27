package net.bestia.geo

import kotlin.math.floor
import kotlin.math.pow

/**
 * Short map references, `H12 41 37`, instead of six-digit metre coordinates.
 *
 * The world is cut into [SQUARE_METRES] squares named like a chessboard: a column letter counted from the west
 * (A..Z, then AA..) and a row number counted from the south, so A1 is the south-west corner. Inside a square the
 * digits work like an Ordnance Survey reference - equal halves east then north, and more digits mean a finer cell.
 * The companion (`gridRef.ts`) and the client (`grid_ref.gd`) carry copies, held to the same cases in
 * `gridref-cases.tsv`.
 */
class GridRef(
  private val worldWidthMetres: Double,
  private val worldHeightMetres: Double,
) {

  /** A parsed reference: the centre of the cell it names, and the cell's edge. */
  data class Point(val eastMetres: Double, val northMetres: Double, val precisionMetres: Double)

  private val columns = Math.ceil(worldWidthMetres / SQUARE_METRES).toInt()
  private val rows = Math.ceil(worldHeightMetres / SQUARE_METRES).toInt()

  /** [digits] per half, 1 (1 km) to 4 (1 m). The world wraps, so any coordinate has a reference. */
  fun format(eastMetres: Double, northMetres: Double, digits: Int = 2): String {
    require(digits in 1..MAX_DIGITS) { "digits must be 1..$MAX_DIGITS, was $digits" }

    val east = wrap(eastMetres, worldWidthMetres)
    val north = wrap(northMetres, worldHeightMetres)
    val column = (east / SQUARE_METRES).toInt()
    val row = (north / SQUARE_METRES).toInt()

    val cell = cellMetres(digits)
    val inEast = floor((east - column * SQUARE_METRES) / cell).toInt()
    val inNorth = floor((north - row * SQUARE_METRES) / cell).toInt()

    return "${columnName(column)}${row + 1} ${inEast.toString().padStart(digits, '0')} " +
        inNorth.toString().padStart(digits, '0')
  }

  /** Null when [text] is not a reference, or names a square or cell outside the world. */
  fun parse(text: String): Point? {
    val match = PATTERN.matchEntire(text.trim()) ?: return null
    val (letters, rowText, first, second) = match.destructured

    val column = columnIndex(letters.uppercase())
    val row = rowText.toInt() - 1
    if (column >= columns || row !in 0 until rows) return null

    val (eastDigits, northDigits) = when {
      first.isEmpty() -> return squareCentre(column, row)
      second.isEmpty() -> {
        if (first.length % 2 != 0) return null
        first.substring(0, first.length / 2) to first.substring(first.length / 2)
      }
      else -> first to second
    }
    if (eastDigits.length != northDigits.length || eastDigits.length > MAX_DIGITS) return null

    val cell = cellMetres(eastDigits.length)
    val east = column * SQUARE_METRES + eastDigits.toInt() * cell
    val north = row * SQUARE_METRES + northDigits.toInt() * cell
    if (east - column * SQUARE_METRES >= SQUARE_METRES || north - row * SQUARE_METRES >= SQUARE_METRES) return null
    if (east >= worldWidthMetres || north >= worldHeightMetres) return null

    return Point(east + cell / 2, north + cell / 2, cell)
  }

  private fun squareCentre(column: Int, row: Int): Point {
    val east = minOf(column * SQUARE_METRES + SQUARE_METRES / 2, worldWidthMetres)
    val north = minOf(row * SQUARE_METRES + SQUARE_METRES / 2, worldHeightMetres)
    return Point(east, north, SQUARE_METRES)
  }

  companion object {
    const val SQUARE_METRES = 8000.0
    const val MAX_DIGITS = 4

    private val PATTERN = Regex("""([A-Za-z]+)\s*(\d+)(?:\s+(\d+)(?:\s+(\d+))?)?""")

    private fun cellMetres(digits: Int): Double {
      return 10.0.pow(MAX_DIGITS - digits)
    }

    private fun wrap(value: Double, size: Double): Double {
      val wrapped = value % size
      return if (wrapped < 0) wrapped + size else wrapped
    }

    /** Spreadsheet-style: 0 is A, 25 is Z, 26 is AA. */
    fun columnName(index: Int): String {
      var n = index + 1
      val name = StringBuilder()
      while (n > 0) {
        val rest = (n - 1) % 26
        name.insert(0, 'A' + rest)
        n = (n - 1) / 26
      }
      return name.toString()
    }

    fun columnIndex(name: String): Int {
      var n = 0
      for (c in name) {
        n = n * 26 + (c - 'A' + 1)
      }
      return n - 1
    }
  }
}
