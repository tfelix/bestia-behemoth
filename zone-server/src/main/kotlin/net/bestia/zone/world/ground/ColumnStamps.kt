package net.bestia.zone.world.ground

/**
 * The shaped marks left on one chunk column, oldest first.
 *
 * ### A ring, because stamps arrive in time order and leave in it
 *
 * Something walking across a column leaves a stamp per tile, for ever, so the store has to be bounded by
 * something other than good behaviour. A ring bounded by [capacity] drops the oldest print when a new one
 * arrives, which is exactly what should be lost: the freshest tracks are the ones anybody is looking at. It
 * also makes expiry a walk from one end rather than a scan, since ages only ever increase along it.
 *
 * ### Two primitive arrays rather than objects
 *
 * Twelve bytes a stamp against thirty-odd for an object with a header, and no garbage at all on a path being
 * walked - which is the allocation-per-footfall this would otherwise be. `GroundStampConfig` does the
 * arithmetic on what that costs a shard.
 *
 * ### The byte layout is a wire contract
 *
 * [toBytes] is the only encoder and `GroundStampCells` on the client is the only decoder. Any byte string is a
 * legal payload, so a disagreement about the layout draws plausible tracks pointing the wrong way rather than
 * failing - which nobody reports as a bug. Pinned by a fixture on both sides.
 */
class ColumnStamps(private val capacity: Int) {

  /** `cellIndex | kind | octant | seed`, see [pack]. */
  private val packed = IntArray(capacity)

  /** When each stamp was laid, in absolute Bestia seconds, ascending from [oldest]. */
  private val laidAt = LongArray(capacity)

  /** Where the ring starts. Entries run from here, wrapping, for [count] of them. */
  private var oldest = 0

  var count: Int = 0
    private set

  /**
   * The second this column's stamps may next be put on the wire.
   *
   * Held here rather than in the registry because it is per column, and it is what keeps a walked column from
   * re-sending its whole stamp set on every single footfall - see `GroundStampConfig.announceIntervalSeconds`.
   */
  var announceDueSecond: Long = Long.MIN_VALUE

  /** Whether anything has happened here that clients have not been told about yet. */
  var pending: Boolean = false

  val isEmpty: Boolean get() = count == 0

  /**
   * Records one stamp, dropping the oldest if the ring is full.
   *
   * @param cellIndex `localY * chunkSize + localX`, the cell order every ground message uses
   * @param octant the eight-connected heading it was left facing, 0 towards +x and counting towards +y
   * @param seed a shape variant, which is what keeps a hundred prints from being one print repeated
   */
  fun add(cellIndex: Int, kind: GroundStampKind, octant: Int, seed: Int, atSecond: Long) {
    require(cellIndex in 0..MAX_CELL_INDEX) { "cell index $cellIndex does not fit the wire's two bytes" }

    val at = (oldest + count) % capacity

    packed[at] = pack(cellIndex, kind, octant, seed)
    laidAt[at] = atSecond

    if (count < capacity) {
      count++
    } else {
      // Full: the write above landed on the oldest entry, so the ring simply starts one later.
      oldest = (oldest + 1) % capacity
    }

    pending = true
  }

  /**
   * Drops everything laid longer ago than [ttlSeconds].
   *
   * A walk from the old end rather than a scan, which is the whole reason the ring is kept in time order.
   *
   * @return whether anything went, so a caller knows to re-announce the column
   */
  fun expire(nowSecond: Long, ttlSeconds: Long): Boolean {
    var dropped = 0

    while (count - dropped > 0 && laidAt[(oldest + dropped) % capacity] + ttlSeconds <= nowSecond) {
      dropped++
    }

    if (dropped == 0) return false

    oldest = (oldest + dropped) % capacity
    count -= dropped
    pending = true

    return true
  }

  /**
   * This column's stamps in wire form, oldest first so the client draws the newest on top.
   *
   * @return null when there is nothing here, which is a column with no stamps rather than an empty payload
   */
  fun toBytes(nowSecond: Long, ttlSeconds: Long): ByteArray? {
    if (count == 0) return null

    val out = ByteArray(count * BYTES_PER_STAMP)
    var write = 0

    for (n in 0 until count) {
      val at = (oldest + n) % capacity
      val stamp = packed[at]
      val cellIndex = stamp and 0xFFFF

      out[write++] = (cellIndex and 0xFF).toByte()
      out[write++] = ((cellIndex ushr 8) and 0xFF).toByte()
      out[write++] = ((((stamp ushr 16) and 0xF) shl 4) or ((stamp ushr 20) and 0x7)).toByte()
      out[write++] = ((stamp ushr 23) and 0xFF).toByte()
      out[write++] = strengthOf(laidAt[at], nowSecond, ttlSeconds).toByte()
    }

    return out
  }

  companion object {

    /** Cell index, kind, octant, seed, strength. See the class note on why this is a contract. */
    const val BYTES_PER_STAMP = 5

    /** Two bytes of cell index, which caps `chunkSize` at 256 - four times what the world uses. */
    const val MAX_CELL_INDEX = 0xFFFF

    /**
     * How many strengths a stamp can be sent at.
     *
     * Quantised rather than continuous so that a print fading is a handful of re-sends over its whole life
     * instead of one per sweep for ever. Sixteen is the same resolution the layer nibbles carry, and for the
     * same reason: past that nobody can see the difference.
     */
    const val STRENGTH_LEVELS = 16

    /**
     * How strongly a stamp laid at [laidAtSecond] should still be drawn.
     *
     * Full when fresh, falling to the lowest step it is sent at rather than to nothing - a stamp that is still
     * here is still visible, and it disappears by expiring rather than by fading to invisible and lingering.
     */
    fun strengthOf(laidAtSecond: Long, nowSecond: Long, ttlSeconds: Long): Int {
      val remaining = (laidAtSecond + ttlSeconds - nowSecond).coerceIn(0, ttlSeconds)
      val level = (remaining * STRENGTH_LEVELS / ttlSeconds).toInt().coerceIn(1, STRENGTH_LEVELS)

      return level * 255 / STRENGTH_LEVELS
    }

    private fun pack(cellIndex: Int, kind: GroundStampKind, octant: Int, seed: Int): Int {
      return (cellIndex and 0xFFFF) or
          ((kind.wireId and 0xF) shl 16) or
          ((octant and 0x7) shl 20) or
          ((seed and 0xFF) shl 23)
    }
  }
}
