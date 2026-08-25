package net.bestia.zone.world.fire

/**
 * One bit per voxel column of one chunk: which square metres of this chunk's ground are marked.
 *
 * ### One type, four readers
 *
 * The scorch a chunk carries, the cells a fire has alight, and both halves of the wire message are the same
 * question asked about the same lattice, so they are the same type rather than four hand-rolled bitsets. The
 * alternative was tried elsewhere and is what `ChunkDelta`'s KDoc warns about: a second representation of one
 * fact is where the two get to disagree.
 *
 * ### Resolution is one metre, and the row being per chunk is a separate thing
 *
 * At `chunkSize = 32` and `voxelSize = 1.0` a chunk column holds 1024 cells, so a whole mask is **128 bytes**
 * - against roughly three kilobytes of deflated chunk payload for the same ground. "Per-chunk mask" is
 * therefore a statement about *storage*, never about resolution: scorch is drawn at a metre, and a reader who
 * takes it for 32 m will size everything downstream wrong.
 *
 * A metre is also the floor. It is what the mesher works in and what a `Position` tile is, so a cell maps to
 * one of each with no conversion - which matters more than it sounds, because the `edge = radius * 2` cube
 * confusion is a documented hazard in this codebase and a second lattice pitch would add another.
 *
 * ### Index order is a wire contract
 *
 * `localY * size + localX`, bit `i` in byte `i / 8` at `i % 8` counting from the least significant. That is
 * `ColumnSummary`'s own indexing, so a reader who knows one knows the other - and it is pinned by a test on
 * both sides of the wire, because any byte string is a legal mask and a decoder that disagrees about bit order
 * produces a plausible pattern rather than an error.
 */
class ColumnMask(val size: Int) {

  private val bits = ByteArray(byteLength(size))

  var count: Int = 0
    private set

  val isEmpty get() = count == 0

  fun indexOf(localX: Int, localY: Int) = localY * size + localX

  operator fun get(index: Int): Boolean =
    (bits[index ushr 3].toInt() shr (index and 7)) and 1 == 1

  operator fun get(localX: Int, localY: Int): Boolean = this[indexOf(localX, localY)]

  fun contains(localX: Int, localY: Int): Boolean =
    localX in 0 until size && localY in 0 until size && this[indexOf(localX, localY)]

  /** @return true if this changed the mask, so a caller can tell a real edit from a no-op without re-reading. */
  fun set(index: Int): Boolean {
    if (this[index]) return false
    bits[index ushr 3] = (bits[index ushr 3].toInt() or (1 shl (index and 7))).toByte()
    count++
    return true
  }

  fun set(localX: Int, localY: Int): Boolean = set(indexOf(localX, localY))

  fun clear(index: Int): Boolean {
    if (!this[index]) return false
    bits[index ushr 3] = (bits[index ushr 3].toInt() and (1 shl (index and 7)).inv()).toByte()
    count--
    return true
  }

  fun clear(localX: Int, localY: Int): Boolean = clear(indexOf(localX, localY))

  /** Every set cell, as `(localX, localY)`. Allocation-free; the receiver is called in index order. */
  inline fun forEachSet(action: (localX: Int, localY: Int) -> Unit) {
    for (index in 0 until size * size) {
      if (this[index]) action(index % size, index / size)
    }
  }

  fun or(other: ColumnMask) {
    require(other.size == size) { "cannot merge a ${other.size}-wide mask into a $size-wide one" }
    for (index in 0 until size * size) if (other[index]) set(index)
  }

  /**
   * A copy with every set cell whose four-neighbourhood is not fully set cleared, [steps] times over.
   *
   * How a scar heals: it shrinks inward from its edges rather than fading uniformly, so a one-cell filament
   * goes in a single step and the middle of a wide burn is the last thing to green over. That is also why the
   * store needs no per-cell timestamp - the *shape* already carries which parts of a scar are its edge.
   *
   * A cell outside the mask counts as unset, so a scar touching a chunk boundary erodes from that edge too.
   * Slightly wrong where a burn genuinely continues into the next chunk, and the alternative is asking the
   * neighbour - which would make healing a cross-chunk operation to save a metre of scar at a seam. Revisit
   * only if that seam is visible in practice.
   */
  fun eroded(steps: Int): ColumnMask {
    if (steps <= 0 || isEmpty) return this

    var current = this
    repeat(steps) {
      if (current.isEmpty) return current

      val next = ColumnMask(size)
      current.forEachSet { x, y ->
        if (current.contains(x - 1, y) && current.contains(x + 1, y) &&
          current.contains(x, y - 1) && current.contains(x, y + 1)
        ) {
          next.set(x, y)
        }
      }
      current = next
    }

    return current
  }

  fun toBytes(): ByteArray = bits.copyOf()

  companion object {

    fun byteLength(size: Int) = (size * size + 7) / 8

    /**
     * @throws IllegalArgumentException if [bytes] is not exactly the length [size] implies. Checked rather than
     *   tolerated: a short array would read as a mask with its tail clear, which is indistinguishable from
     *   ground that genuinely has nothing on it.
     */
    fun fromBytes(size: Int, bytes: ByteArray): ColumnMask {
      require(bytes.size == byteLength(size)) {
        "a $size-wide mask is ${byteLength(size)} bytes, got ${bytes.size}"
      }

      val mask = ColumnMask(size)
      for (index in 0 until size * size) {
        if ((bytes[index ushr 3].toInt() shr (index and 7)) and 1 == 1) mask.set(index)
      }
      return mask
    }
  }
}
