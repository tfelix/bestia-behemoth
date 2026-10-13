package net.bestia.zone.ground

/**
 * How strongly one ground layer marks each square metre of one chunk column.
 *
 * The graded sibling of [ColumnMask]: same lattice, same index order, a byte per cell instead of a bit.
 *
 * ### A byte here, and a nibble or a byte on the wire
 *
 * Sixteen levels is the right resolution to *draw* and the wrong one to *accumulate*. One footfall has to be
 * worth far less than one visible step, or the first creature across a meadow leaves a path; with sixteen
 * levels the smallest increment it could make is a sixteenth of the way to bare earth. So traffic accumulates
 * in 0..255. A layer nobody adds to is sent as [toNibbles]. A layer a client keeps adding to itself is sent as
 * [toSparseLevels], exact, because a client seeded from nibbles would cross the visible threshold at a
 * different footfall than the server did.
 *
 * ### Decay is subtraction, and uniform is correct
 *
 * [decayBy] takes the same amount off every cell. That looks too blunt until you notice a heavily walked cell
 * holds a higher level and therefore survives proportionally longer, so a path narrows to its most-used line
 * rather than fading evenly - which is what a real desire path does. It also means no per-cell clock, the
 * saving `ScorchMark` makes for the same reason.
 *
 * ### Index order and both byte layouts are wire contracts
 *
 * Cells are `localY * size + localX`, as [ColumnMask] and `ColumnSummary` both are. Cell `i`'s nibble lives in
 * byte `i / 2`, low nibble first. Any byte string is a legal payload, so a decoder that disagrees draws a
 * plausible pattern of wear in the wrong places rather than failing - hence the fixture tests on both sides.
 */
class ColumnLevels(val size: Int) {

  private val levels = ByteArray(size * size)

  /** Cells above zero, so [isEmpty] costs nothing and a spent column can be dropped without a scan. */
  var markedCells: Int = 0
    private set

  val isEmpty: Boolean
    get() {
      return markedCells == 0
    }

  fun indexOf(localX: Int, localY: Int): Int {
    return localY * size + localX
  }

  operator fun get(index: Int): Int {
    return levels[index].toInt() and 0xFF
  }

  operator fun get(localX: Int, localY: Int): Int {
    return this[indexOf(localX, localY)]
  }

  /** What the client would draw for this cell: [get] quantised to the four bits the wire carries. */
  fun nibbleAt(index: Int): Int {
    return this[index] shr 4
  }

  /**
   * Adds [amount] to one cell, saturating at 255.
   *
   * @return true if the *drawn* level changed, so a caller can tell a deepening path from one merely walked
   *   again and skip queueing the column for a resync.
   */
  fun add(index: Int, amount: Int): Boolean {
    require(amount >= 0) { "wear is added, never subtracted one cell at a time; use decayBy" }

    val before = this[index]
    if (before == 0 && amount > 0) markedCells++

    val after = minOf(255, before + amount)
    levels[index] = after.toByte()

    return (before shr 4) != (after shr 4)
  }

  fun add(localX: Int, localY: Int, amount: Int): Boolean {
    return add(indexOf(localX, localY), amount)
  }

  /**
   * Takes [amount] off every marked cell, clamped at zero.
   *
   * @return true if any drawn level changed
   */
  fun decayBy(amount: Int): Boolean {
    if (amount <= 0 || isEmpty) return false

    var redraw = false
    for (index in levels.indices) {
      val before = this[index]
      if (before == 0) continue

      val after = maxOf(0, before - amount)
      levels[index] = after.toByte()
      if (after == 0) markedCells--
      if ((before shr 4) != (after shr 4)) redraw = true
    }

    return redraw
  }

  /** Every marked cell, as `(localX, localY, level)`. Allocation-free; the receiver is called in index order. */
  inline fun forEachMarked(action: (localX: Int, localY: Int, level: Int) -> Unit) {
    for (index in 0 until size * size) {
      val level = this[index]
      if (level > 0) action(index % size, index / size, level)
    }
  }

  /** The wire form: two cells to a byte, low nibble first. See the class note. */
  fun toNibbles(): ByteArray {
    val packed = ByteArray(nibbleLength(size))
    for (index in levels.indices) {
      val nibble = nibbleAt(index)
      if (nibble == 0) continue

      val at = index shr 1
      packed[at] = if (index and 1 == 0) {
        (packed[at].toInt() or nibble).toByte()
      } else {
        (packed[at].toInt() or (nibble shl 4)).toByte()
      }
    }
    return packed
  }

  /**
   * The wire form for a layer the client adds to itself: `(cell index u16 little endian, level u8)` for every
   * non-zero cell, ascending. Sub-threshold cells are included - see the class note.
   */
  fun toSparseLevels(): ByteArray {
    val out = ByteArray(markedCells * SPARSE_BYTES_PER_CELL)
    var write = 0

    for (index in levels.indices) {
      val level = this[index]
      if (level == 0) continue

      out[write++] = (index and 0xFF).toByte()
      out[write++] = ((index ushr 8) and 0xFF).toByte()
      out[write++] = level.toByte()
    }

    return out
  }

  fun toBytes(): ByteArray {
    return levels.copyOf()
  }

  companion object {

    /** The strongest a cell can be marked, being every bit of the byte it is stored in. */
    const val MAX_LEVEL = 255

    /** Cell index (two bytes) and level, per cell of [toSparseLevels]. */
    const val SPARSE_BYTES_PER_CELL = 3

    fun nibbleLength(size: Int): Int {
      return (size * size + 1) / 2
    }

    /**
     * @throws IllegalArgumentException if [bytes] is not exactly `size * size` long. Checked rather than
     *   tolerated: a short array reads as a column whose tail is unworn, which is indistinguishable from
     *   ground nobody has ever walked on.
     */
    fun fromBytes(size: Int, bytes: ByteArray): ColumnLevels {
      require(bytes.size == size * size) {
        "a $size-wide column holds ${size * size} cells, got ${bytes.size}"
      }

      val grid = ColumnLevels(size)
      bytes.copyInto(grid.levels)
      grid.markedCells = bytes.count { it.toInt() != 0 }
      return grid
    }
  }
}
