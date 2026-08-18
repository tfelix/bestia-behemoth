package net.bestia.zone.cartography.coverage

import net.bestia.worldgen.core.WorldWrap
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The lattice a chart records its discovered area on.
 *
 * One bit per cell, and a cell is [CELL_METRES] square. That is the whole of the fog model: a chart says which
 * cells its surveyor walked into range of, and everything else is under fog.
 *
 * ### Why 64 m, and why it is not a setting
 *
 * The number is a trade between bytes and how coarse the edge of a chart looks, and the two ends of the zoom
 * ladder pull in opposite directions because a cell is a fixed number of metres while a pixel is not. At the
 * world map's default zoom (128 m per pixel) a cell is half a pixel and its size is invisible. On the minimap
 * (4 m per pixel, a kilometre across a 256 pixel widget) a cell is 16 pixels, and at 1 m per pixel it is 64 -
 * so the close-zoom end is what decides this, not the world map. 256 m, the first candidate, put a single cell
 * at a quarter of the minimap and made fog there all-or-nothing.
 *
 * Against that, cells cost bytes quadratically: at 64 m a fully charted 128 km world is 2000 x 2000 bits, half
 * a megabyte raw, which [CoverageCodec] deflates to a fraction of that because a solid region is a run of set
 * bits. 32 m - one chunk column, tidily - would be two megabytes raw for precision no zoom level can resolve.
 *
 * It is a `const`, not a configuration property, and that is deliberate rather than lazy: the stored bits are
 * *positional*, so re-reading a chart at a different cell size silently reinterprets where its owner has been.
 * A knob whose every change corrupts existing data is not a knob. [CoverageCodec] writes the size into the blob
 * so a mismatch is detectable rather than silent.
 *
 * ### Wrapping
 *
 * Cell indices are always inside the grid. A world with [WorldWrap.wrapsX] folds east into west, so a survey
 * near the seam wraps like everything else; on an axis that does not wrap, cells outside the world are dropped
 * rather than stored. Callers may hand in any world coordinate, normalised or not.
 */
class SurveyGrid(private val wrap: WorldWrap) {

  /** Cells across the world. The last one is partial if the world is not a whole number of cells wide. */
  val cellsAcross: Int = ceil(wrap.width / CELL_METRES).toInt()

  val cellsDown: Int = ceil(wrap.height / CELL_METRES).toInt()

  /** The cell containing a world position, normalised. */
  fun cellXOf(worldX: Double): Int = normaliseX(rawCellXOf(worldX))

  fun cellYOf(worldY: Double): Int = normaliseY(rawCellYOf(worldY))

  /**
   * The cell index a world coordinate falls in *without* wrapping, which may be outside the grid.
   *
   * What a caller iterating an area wants: a tile's bounds can reach past the world edge, and folding each
   * coordinate before the loop would turn one rectangle into an unpredictable number of them. Fold the indices
   * afterwards with [normaliseX] or [foldX].
   */
  fun rawCellXOf(worldX: Double): Int = floor(worldX / CELL_METRES).toInt()

  fun rawCellYOf(worldY: Double): Int = floor(worldY / CELL_METRES).toInt()

  /**
   * Last cell of the half-open span ending at [worldX], without wrapping.
   *
   * A tile's east edge is its neighbour's west edge, so an area's maximum must not pull in the cell beyond it -
   * using [rawCellXOf] there would make every tile claim a one-cell strip of the next one and report coverage
   * it does not have.
   */
  fun rawCellXBefore(worldX: Double): Int = ceil(worldX / CELL_METRES).toInt() - 1

  fun rawCellYBefore(worldY: Double): Int = ceil(worldY / CELL_METRES).toInt() - 1

  /** Whether a raw cell index has a place in the grid at all. False only on an axis that does not wrap. */
  fun holdsX(cellX: Int): Boolean = wrap.wrapsX || cellX in 0 until cellsAcross

  fun holdsY(cellY: Int): Boolean = wrap.wrapsY || cellY in 0 until cellsDown

  /** The canonical index for a possibly out-of-range one. Only meaningful where [holdsX] is true. */
  fun normaliseX(cellX: Int): Int = if (wrap.wrapsX) Math.floorMod(cellX, cellsAcross) else cellX

  fun normaliseY(cellY: Int): Int = if (wrap.wrapsY) Math.floorMod(cellY, cellsDown) else cellY

  /**
   * A raw index range folded into the grid, as one or two in-range ranges.
   *
   * Two when the range crosses the seam, and one otherwise - which is what lets a block-at-a-time scan work on
   * a wrapped world: each returned range is a contiguous run of real cells, so it maps onto whole blocks. A
   * range at least as wide as the world folds to the whole width rather than to overlapping pieces, so a caller
   * summing cell counts over the result never counts a cell twice.
   */
  fun foldX(fromCellX: Int, toCellX: Int): List<IntRange> = fold(fromCellX, toCellX, cellsAcross, wrap.wrapsX)

  fun foldY(fromCellY: Int, toCellY: Int): List<IntRange> = fold(fromCellY, toCellY, cellsDown, wrap.wrapsY)

  /** West edge of a cell, in world metres. */
  fun cellMinX(cellX: Int): Double = cellX * CELL_METRES

  fun cellMinY(cellY: Int): Double = cellY * CELL_METRES

  /** Centre of a cell. What a membership test should use, so a cell is in or out as a whole. */
  fun cellCentreX(cellX: Int): Double = (cellX + 0.5) * CELL_METRES

  fun cellCentreY(cellY: Int): Double = (cellY + 0.5) * CELL_METRES

  /** Seam-aware distance, for deciding whether a cell is inside a survey. */
  fun distance(fromX: Double, fromY: Double, toX: Double, toY: Double): Double =
    wrap.distance(fromX, fromY, toX, toY)

  private fun fold(from: Int, to: Int, extent: Int, wraps: Boolean): List<IntRange> {
    if (!wraps) {
      val clamped = from.coerceAtLeast(0)..to.coerceAtMost(extent - 1)
      return if (clamped.isEmpty()) emptyList() else listOf(clamped)
    }

    if (to - from + 1 >= extent) return listOf(0 until extent)

    val start = Math.floorMod(from, extent)
    val end = start + (to - from)

    return if (end < extent) listOf(start..end) else listOf(start until extent, 0..(end - extent))
  }

  companion object {

    /** Edge of one fog cell, in metres. See the class note - changing this reinterprets every stored chart. */
    const val CELL_METRES = 64.0
  }
}
