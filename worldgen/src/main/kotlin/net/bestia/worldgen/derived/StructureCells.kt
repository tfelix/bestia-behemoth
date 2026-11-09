package net.bestia.worldgen.derived

import net.bestia.worldgen.voxel.Occupancy

/**
 * The cells player-built pieces fill in one chunk: an overlay beside the terrain voxels, never written into them.
 *
 * A cell is one voxel position. Its [fillAt] is how full it is from the bottom up, as an [Occupancy] byte: a floor
 * slab or a half stair step is partly full, a block is full, and a cell holding only walls is empty. Its [edgesAt]
 * are the walls on its borders as [Edges] bits. A wall on a border is written into the cells on both sides of it,
 * so a chunk never has to look into its neighbour.
 *
 * Immutable, so a snapshot can be handed to a worker thread that builds a [WalkableTile] from it.
 */
class StructureCells private constructor(
  val size: Int,
  private val columnMask: LongArray,
  /** CSR offsets into the cell arrays, length `size * size + 1`. */
  private val columnStart: IntArray,
  /** Local z of each cell, ascending within a column. */
  private val cellZ: ShortArray,
  private val cellFill: ByteArray,
  private val cellEdges: ByteArray
) {

  val cellCount: Int
    get() {
      return cellZ.size
    }

  /** A cheap test that lets a column without any piece skip every other lookup. */
  fun hasColumn(localX: Int, localY: Int): Boolean {
    val column = localY * size + localX

    return columnMask[column ushr 6] and (1L shl column) != 0L
  }

  /** The [Occupancy] of a built cell, or [Occupancy.EMPTY] where nothing fills it. */
  fun fillAt(localX: Int, localY: Int, localZ: Int): Int {
    val cell = cellIndex(localX, localY, localZ)
    if (cell < 0) return Occupancy.EMPTY

    return Occupancy.unsigned(cellFill[cell])
  }

  fun edgesAt(localX: Int, localY: Int, localZ: Int): Int {
    val cell = cellIndex(localX, localY, localZ)
    if (cell < 0) return Edges.NONE

    return cellEdges[cell].toInt()
  }

  /** Every wall on the borders of a column between [fromZ] and [toZ], both included. */
  fun edgesBetween(localX: Int, localY: Int, fromZ: Int, toZ: Int): Int {
    if (!hasColumn(localX, localY)) return Edges.NONE

    val column = localY * size + localX
    var edges = Edges.NONE
    for (cell in columnStart[column] until columnStart[column + 1]) {
      if (cellZ[cell] in fromZ..toZ) edges = edges or cellEdges[cell].toInt()
    }

    return edges
  }

  private fun cellIndex(localX: Int, localY: Int, localZ: Int): Int {
    if (!hasColumn(localX, localY)) return -1

    val column = localY * size + localX
    for (cell in columnStart[column] until columnStart[column + 1]) {
      if (cellZ[cell].toInt() == localZ) return cell
    }

    return -1
  }

  /** Collects cells in any order. Two pieces in one cell share it: the fuller fill wins and the walls add up. */
  class Builder(private val size: Int) {

    private val cells = HashMap<Int, Int>()

    fun add(localX: Int, localY: Int, localZ: Int, fill: Int, edges: Int): Builder {
      require(localX in 0 until size && localY in 0 until size) { "($localX,$localY) is outside a chunk of $size" }
      require(localZ in 0..Short.MAX_VALUE) { "local z $localZ does not fit a cell" }

      val key = (localY * size + localX) * CELL_Z_RANGE + localZ
      val merged = cells[key]?.let { packed ->
        pack(maxOf(fillOf(packed), fill), edgesOf(packed) or edges)
      } ?: pack(fill, edges)
      cells[key] = merged

      return this
    }

    fun build(): StructureCells {
      val columns = size * size
      val keys = cells.keys.sorted()

      val columnMask = LongArray((columns + 63) / 64)
      val columnStart = IntArray(columns + 1)
      val cellZ = ShortArray(keys.size)
      val cellFill = ByteArray(keys.size)
      val cellEdges = ByteArray(keys.size)

      for ((cell, key) in keys.withIndex()) {
        val column = key / CELL_Z_RANGE
        val packed = cells.getValue(key)
        columnMask[column ushr 6] = columnMask[column ushr 6] or (1L shl column)
        columnStart[column + 1]++
        cellZ[cell] = (key % CELL_Z_RANGE).toShort()
        cellFill[cell] = fillOf(packed).toByte()
        cellEdges[cell] = edgesOf(packed).toByte()
      }
      for (column in 0 until columns) {
        columnStart[column + 1] += columnStart[column]
      }

      return StructureCells(size, columnMask, columnStart, cellZ, cellFill, cellEdges)
    }

    private fun pack(fill: Int, edges: Int): Int {
      return (fill shl 8) or (edges and Edges.ALL)
    }

    private fun fillOf(packed: Int): Int {
      return packed ushr 8
    }

    private fun edgesOf(packed: Int): Int {
      return packed and 0xFF
    }

    private companion object {
      const val CELL_Z_RANGE = Short.MAX_VALUE + 1
    }
  }
}
