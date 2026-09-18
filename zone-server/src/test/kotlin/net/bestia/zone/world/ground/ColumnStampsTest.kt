package net.bestia.zone.world.ground

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ring of prints on one column: what it keeps, what it drops, and the bytes it puts on the wire.
 *
 * That last one is a contract rather than an implementation detail - any byte string decodes to *something*, so
 * a client that disagrees draws tracks pointing the wrong way rather than failing. `GroundStampCellsTest` on
 * the client pins the same fixture from the other side.
 */
class ColumnStampsTest {

  private val ttl = 800L

  @Test
  fun `a stamp comes back in the order it was laid`() {
    val stamps = ColumnStamps(capacity = 4)

    stamps.add(cellIndex = 1, kind = GroundStampKind.FOOTPRINT, octant = 0, seed = 0, atSecond = 100)
    stamps.add(cellIndex = 2, kind = GroundStampKind.FOOTPRINT, octant = 0, seed = 0, atSecond = 200)

    val bytes = stamps.toBytes(nowSecond = 200, ttlSeconds = ttl)!!

    assertEquals(2, stamps.count)
    assertEquals(1, bytes[0].toInt())
    assertEquals(2, bytes[ColumnStamps.BYTES_PER_STAMP].toInt())
  }

  /** The bound. Something crossing a column for the tenth time must cost what the first crossing did. */
  @Test
  fun `a full ring drops the oldest print rather than growing`() {
    val stamps = ColumnStamps(capacity = 3)

    for (cell in 1..5) {
      stamps.add(cellIndex = cell, kind = GroundStampKind.FOOTPRINT, octant = 0, seed = 0, atSecond = cell.toLong())
    }

    val bytes = stamps.toBytes(nowSecond = 5, ttlSeconds = ttl)!!

    assertEquals(3, stamps.count)
    assertEquals(3 * ColumnStamps.BYTES_PER_STAMP, bytes.size)
    assertEquals(3, bytes[0].toInt(), "the oldest surviving print should be the third one laid")
  }

  @Test
  fun `expiry takes the old end and leaves the rest`() {
    val stamps = ColumnStamps(capacity = 8)

    stamps.add(cellIndex = 1, kind = GroundStampKind.FOOTPRINT, octant = 0, seed = 0, atSecond = 100)
    stamps.add(cellIndex = 2, kind = GroundStampKind.FOOTPRINT, octant = 0, seed = 0, atSecond = 500)

    assertTrue(stamps.expire(nowSecond = 100 + ttl, ttlSeconds = ttl))

    assertEquals(1, stamps.count)
    assertEquals(2, stamps.toBytes(nowSecond = 900, ttlSeconds = ttl)!![0].toInt())
  }

  @Test
  fun `expiring nothing is not a change`() {
    val stamps = ColumnStamps(capacity = 8)
    stamps.add(cellIndex = 1, kind = GroundStampKind.FOOTPRINT, octant = 0, seed = 0, atSecond = 100)
    stamps.pending = false

    assertFalse(stamps.expire(nowSecond = 200, ttlSeconds = ttl))
    assertFalse(stamps.pending, "a sweep that removed nothing announced the column anyway")
  }

  @Test
  fun `a column with nothing on it encodes to nothing at all`() {
    val stamps = ColumnStamps(capacity = 4)

    assertTrue(stamps.isEmpty)
    assertNull(stamps.toBytes(nowSecond = 0, ttlSeconds = ttl))
  }

  /** Expiry has to work across the wrap, or a column walked twice starts keeping prints for ever. */
  @Test
  fun `expiry still finds the old end after the ring has wrapped`() {
    val stamps = ColumnStamps(capacity = 3)

    for (cell in 1..5) {
      stamps.add(
        cellIndex = cell,
        kind = GroundStampKind.FOOTPRINT,
        octant = 0,
        seed = 0,
        atSecond = cell * 100L
      )
    }

    // Prints at 300 and 400 are past it, the one at 500 is not.
    assertTrue(stamps.expire(nowSecond = 400 + ttl, ttlSeconds = ttl))

    assertEquals(1, stamps.count)
    assertEquals(5, stamps.toBytes(nowSecond = 400 + ttl, ttlSeconds = ttl)!![0].toInt())
  }

  @Test
  fun `a fresh print is drawn at full strength and an old one is not`() {
    val fresh = ColumnStamps.strengthOf(laidAtSecond = 1_000, nowSecond = 1_000, ttlSeconds = ttl)
    val old = ColumnStamps.strengthOf(laidAtSecond = 1_000, nowSecond = 1_600, ttlSeconds = ttl)

    assertEquals(255, fresh)
    assertTrue(old in 1..254, "an aged print came back at $old")
  }

  /**
   * Quantised, so a fading print is a handful of re-sends over its whole life rather than one per sweep. A
   * continuous strength here would make every column with a print on it dirty on every single pass.
   */
  @Test
  fun `strength moves in steps rather than continuously`() {
    val steps = (0..ttl step 7)
      .map { ColumnStamps.strengthOf(laidAtSecond = 0, nowSecond = it, ttlSeconds = ttl) }
      .distinct()

    assertTrue(steps.size <= ColumnStamps.STRENGTH_LEVELS, "strength took ${steps.size} distinct values")
  }

  @Test
  fun `a print that is still here is still visible`() {
    // The last instant before expiry. Fading to zero and lingering would leave invisible prints in the ring.
    assertTrue(ColumnStamps.strengthOf(laidAtSecond = 0, nowSecond = ttl - 1, ttlSeconds = ttl) > 0)
  }

  /**
   * **The wire fixture.** Mirrored by `GroundStampCellsTest` on the client; if these two ever disagree the
   * tracks point the wrong way and nothing throws.
   */
  @Test
  fun `the packed bytes are exactly what the client is promised`() {
    val stamps = ColumnStamps(capacity = 4)

    // Half a life old, so it encodes at half strength.
    stamps.add(cellIndex = 0, kind = GroundStampKind.FOOTPRINT, octant = 0, seed = 1, atSecond = 600)
    // localX 3, localY 5 in a 32-wide column.
    stamps.add(cellIndex = 5 * 32 + 3, kind = GroundStampKind.FOOTPRINT, octant = 6, seed = 200, atSecond = 1_000)

    val bytes = stamps.toBytes(nowSecond = 1_000, ttlSeconds = ttl)!!

    assertContentEquals(
      byteArrayOf(
        0x00, 0x00, 0x10, 0x01, 0x7F.toByte(),
        0xA3.toByte(), 0x00, 0x16, 0xC8.toByte(), 0xFF.toByte(),
      ),
      bytes
    )
  }
}
