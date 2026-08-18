package net.bestia.zone.cartography.coverage

import net.bestia.worldgen.vector.Aabb
import java.lang.Long.bitCount
import java.lang.Long.numberOfLeadingZeros
import java.lang.Long.numberOfTrailingZeros

/**
 * The charted area of a world, as one bit per [SurveyGrid] cell.
 *
 * ### Sparse in blocks, dense inside one
 *
 * A chart covers a few discs, so the world is mostly unset and a flat bitset over it would be almost all zeroes
 * - 500 kB for a 128 km world, for a survey that touches a fiftieth of it. But *within* a survey the bits are
 * solid, so a per-cell structure (a set of packed indices, say) would pay a pointer for every cell of a region
 * that a bitmask describes in one word.
 *
 * The shape that fits both is a map from block to bitmask: [BLOCK_CELLS] square blocks, one `Long` per row, so
 * a block is 512 bytes covering 4.1 km of world. An empty region costs nothing because its block is absent, and
 * a solid one costs 512 bytes with no per-cell overhead. Union is a per-block `or`, and the questions the tile
 * service asks - is this area entirely charted, entirely unknown, or which parts of it - are answered a `Long`
 * at a time rather than a cell at a time, which is what makes [coverageOf] affordable on a world-map tile that
 * spans four million cells.
 *
 * Hand-rolled rather than a bitmap library, matching `ChunkPatchCodec` and `RleCodec`: the operations are
 * `or`, `bitCount` and a bounded scan, and the format has to stay stable across releases because it is
 * persisted.
 *
 * ### Mutable, and merges are explicit
 *
 * Filling a disc mutates. Merging does not: [unionWith] returns a new instance, because a merge in the game
 * produces a new chart and must not disturb either of its inputs - the second one is consumed, and consumption
 * is the item layer's business rather than this class's.
 */
class Coverage(val grid: SurveyGrid) {

  private val blocks = HashMap<Long, LongArray>()

  val isEmpty: Boolean get() = blocks.isEmpty()

  /** Whether a world position has been charted. Any spelling of the position; it is normalised here. */
  fun contains(worldX: Double, worldY: Double): Boolean =
    containsCell(grid.rawCellXOf(worldX), grid.rawCellYOf(worldY))

  /** Whether a cell has been charted. Takes raw indices, so a caller scanning past the world edge needs no care. */
  fun containsCell(rawCellX: Int, rawCellY: Int): Boolean {
    if (!grid.holdsX(rawCellX) || !grid.holdsY(rawCellY)) return false

    val cellX = grid.normaliseX(rawCellX)
    val cellY = grid.normaliseY(rawCellY)
    val rows = blocks[keyOf(cellX / BLOCK_CELLS, cellY / BLOCK_CELLS)] ?: return false

    return rows[cellY % BLOCK_CELLS] and (1L shl (cellX % BLOCK_CELLS)) != 0L
  }

  /**
   * Charts every cell whose centre is within [radiusMetres] of a point, and returns how many were new.
   *
   * The centre decides, so a cell is charted as a whole or not at all - a half-covered cell would have to be
   * stored as something other than a bit. Distance is seam-aware, so a survey beside the world's edge charts
   * the ground on the far side of the wrap, which is genuinely a few hundred metres away.
   */
  fun fillDisc(centreX: Double, centreY: Double, radiusMetres: Double): Int {
    require(radiusMetres > 0.0) { "A survey radius must be positive, was $radiusMetres" }

    var added = 0
    val fromY = grid.rawCellYOf(centreY - radiusMetres)
    val toY = grid.rawCellYOf(centreY + radiusMetres)
    val fromX = grid.rawCellXOf(centreX - radiusMetres)
    val toX = grid.rawCellXOf(centreX + radiusMetres)

    for (rawY in fromY..toY) {
      if (!grid.holdsY(rawY)) continue

      for (rawX in fromX..toX) {
        if (!grid.holdsX(rawX)) continue

        val distance = grid.distance(centreX, centreY, grid.cellCentreX(rawX), grid.cellCentreY(rawY))
        if (distance > radiusMetres) continue

        if (setCell(grid.normaliseX(rawX), grid.normaliseY(rawY))) added++
      }
    }

    return added
  }

  /** Adds everything [other] holds. */
  fun orWith(other: Coverage) {
    require(other.grid.cellsAcross == grid.cellsAcross && other.grid.cellsDown == grid.cellsDown) {
      "Coverage from a ${other.grid.cellsAcross}x${other.grid.cellsDown} grid cannot merge into " +
          "${grid.cellsAcross}x${grid.cellsDown} - the bits mean different places"
    }

    for ((key, theirs) in other.blocks) {
      val mine = blocks.getOrPut(key) { LongArray(BLOCK_CELLS) }
      for (row in mine.indices) mine[row] = mine[row] or theirs[row]
    }
  }

  /** The union of two coverages, leaving both untouched. What a chart merge produces. */
  fun unionWith(other: Coverage): Coverage = copy().also { it.orWith(other) }

  fun copy(): Coverage = Coverage(grid).also { clone ->
    for ((key, rows) in blocks) clone.blocks[key] = rows.copyOf()
  }

  fun cellCount(): Long {
    var count = 0L
    for (rows in blocks.values) for (bits in rows) count += bitCount(bits)
    return count
  }

  /**
   * The box enclosing every charted cell, or null if nothing is charted.
   *
   * Loose on a wrapped axis: coverage either side of the seam is two far-apart runs in normalised coordinates,
   * so the box spans the world even though the region is small. That is the honest answer in these coordinates
   * and it is only used to decide what to pre-render, where "too much" costs time and "too little" would be a
   * blank tile.
   */
  fun bounds(): Aabb? {
    if (blocks.isEmpty()) return null

    var minCellX = Int.MAX_VALUE
    var minCellY = Int.MAX_VALUE
    var maxCellX = Int.MIN_VALUE
    var maxCellY = Int.MIN_VALUE

    for ((key, rows) in blocks) {
      val originX = blockX(key) * BLOCK_CELLS
      val originY = blockY(key) * BLOCK_CELLS

      for (row in rows.indices) {
        val bits = rows[row]
        if (bits == 0L) continue

        val cellY = originY + row
        if (cellY < minCellY) minCellY = cellY
        if (cellY > maxCellY) maxCellY = cellY

        val west = originX + numberOfTrailingZeros(bits)
        val east = originX + (BLOCK_CELLS - 1 - numberOfLeadingZeros(bits))
        if (west < minCellX) minCellX = west
        if (east > maxCellX) maxCellX = east
      }
    }

    return Aabb(
      grid.cellMinX(minCellX), grid.cellMinY(minCellY),
      grid.cellMinX(maxCellX + 1), grid.cellMinY(maxCellY + 1)
    )
  }

  /**
   * How much of an area is charted: all, none, or a digest of the part that is.
   *
   * The area is half-open on its maximum edges, so a tile and the tile east of it do not both claim the column
   * of cells between them.
   *
   * Costs one pass over the blocks the area touches rather than one per cell, which is the difference between
   * microseconds and tens of milliseconds on a world-map tile.
   */
  fun coverageOf(area: Aabb): AreaCoverage {
    var cells = 0L
    var charted = 0L
    var digest = SEED

    val xs = grid.foldX(grid.rawCellXOf(area.minX), grid.rawCellXBefore(area.maxX))
    val ys = grid.foldY(grid.rawCellYOf(area.minY), grid.rawCellYBefore(area.maxY))

    for (columns in xs) {
      for (rows in ys) {
        forEachBlock(columns, rows) { key, block, columnMask, fromRow, toRow ->
          cells += (toRow - fromRow + 1).toLong() * bitCount(columnMask)
          if (block != null) {
            for (row in fromRow..toRow) {
              val bits = block[row] and columnMask
              if (bits == 0L) continue

              charted += bitCount(bits)
              digest = mix(digest * GOLDEN + mix(bits) + key + row)
            }
          }
        }
      }
    }

    return when {
      charted == 0L -> AreaCoverage.None
      charted == cells -> AreaCoverage.Full
      else -> AreaCoverage.Partial(digest)
    }
  }

  /** Sorted by block key, which is [blockY] within [blockX] - the order [CoverageCodec] delta-encodes in. */
  internal fun blockEntries(): List<Pair<Long, LongArray>> =
    blocks.entries.sortedBy { it.key }.map { it.key to it.value }

  internal fun putBlock(key: Long, rows: LongArray) {
    require(rows.size == BLOCK_CELLS) { "A block is $BLOCK_CELLS rows, got ${rows.size}" }
    require(blockX(key) >= 0 && blockY(key) >= 0) { "Block key $key is outside the grid" }

    // An all-zero block would be indistinguishable from an absent one to every reader, but it would still be
    // written out again on the next encode, so a chart that had been merged and re-split could accumulate them.
    if (rows.any { it != 0L }) blocks[key] = rows
  }

  private fun setCell(cellX: Int, cellY: Int): Boolean {
    val rows = blocks.getOrPut(keyOf(cellX / BLOCK_CELLS, cellY / BLOCK_CELLS)) { LongArray(BLOCK_CELLS) }
    val row = cellY % BLOCK_CELLS
    val bit = 1L shl (cellX % BLOCK_CELLS)

    if (rows[row] and bit != 0L) return false

    rows[row] = rows[row] or bit
    return true
  }

  /**
   * Visits every block meeting a cell rectangle, with the columns of that block the rectangle selects.
   *
   * One mask for the whole block rather than one per row, because the rectangle is axis-aligned: every row of a
   * given block is clipped to the same span of columns. Absent blocks are visited with a null [LongArray] so a
   * caller counting the cells of an area sees them all, charted or not.
   */
  private inline fun forEachBlock(
    columns: IntRange,
    rows: IntRange,
    visit: (key: Long, block: LongArray?, columnMask: Long, fromRow: Int, toRow: Int) -> Unit
  ) {
    for (blockY in rows.first / BLOCK_CELLS..rows.last / BLOCK_CELLS) {
      val originY = blockY * BLOCK_CELLS
      val fromRow = maxOf(rows.first, originY) - originY
      val toRow = minOf(rows.last, originY + BLOCK_CELLS - 1) - originY

      for (blockX in columns.first / BLOCK_CELLS..columns.last / BLOCK_CELLS) {
        val originX = blockX * BLOCK_CELLS
        val fromColumn = maxOf(columns.first, originX) - originX
        val toColumn = minOf(columns.last, originX + BLOCK_CELLS - 1) - originX

        val key = keyOf(blockX, blockY)
        visit(key, blocks[key], maskOf(fromColumn, toColumn), fromRow, toRow)
      }
    }
  }

  companion object {

    /** Cells along one edge of a block, and therefore bits in one stored row. */
    const val BLOCK_CELLS = 64

    /** Bytes one block occupies: [BLOCK_CELLS] rows of eight. */
    const val BLOCK_BYTES = BLOCK_CELLS * 8

    /** World metres one block spans. */
    const val BLOCK_METRES = BLOCK_CELLS * SurveyGrid.CELL_METRES

    internal fun keyOf(blockX: Int, blockY: Int): Long = (blockX.toLong() shl 32) or blockY.toLong()

    internal fun blockX(key: Long): Int = (key shr 32).toInt()

    internal fun blockY(key: Long): Int = key.toInt()

    /** Bits [from] through [to] inclusive. Separate case for the whole word because `1L shl 64` is `1L`. */
    private fun maskOf(from: Int, to: Int): Long {
      val count = to - from + 1
      return if (count == BLOCK_CELLS) -1L else ((1L shl count) - 1L) shl from
    }

    /**
     * Golden-ratio and SplitMix64 constants, as `val` rather than `const val`.
     *
     * `0x…uL.toLong()` is not a compile-time constant expression in Kotlin, and writing these out as signed
     * decimals to satisfy `const` would be four opportunities to mistype a nineteen-digit number for no gain.
     */
    private val GOLDEN = 0x9E3779B97F4A7C15uL.toLong()
    private val MIX_A = 0xBF58476D1CE4E5B9uL.toLong()
    private val MIX_B = 0x94D049BB133111EBuL.toLong()
    private val SEED = 0x6A09E667F3BCC908uL.toLong()

    /** SplitMix64's finaliser. Avalanches, so a single differing bit changes the digest everywhere. */
    private fun mix(value: Long): Long {
      var z = value
      z = (z xor (z ushr 30)) * MIX_A
      z = (z xor (z ushr 27)) * MIX_B
      return z xor (z ushr 31)
    }
  }
}
