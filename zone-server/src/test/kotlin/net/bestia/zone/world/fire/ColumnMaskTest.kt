package net.bestia.zone.world.fire

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The mask, and in particular the two things about it that are contracts rather than conveniences.
 *
 * **Bit order**, because it is on the wire. Any byte string is a legal mask, so a client that disagrees about
 * which bit is cell (1, 0) draws a plausible pattern rather than failing - the same failure mode
 * `ChunkPatchEncoding` exists to make impossible for patches. The fixture below is what the C# side has to
 * agree with.
 *
 * **Erosion**, because it is the whole regrowth mechanism. A scar heals by shrinking from its edges, and if it
 * shrank uniformly or from the middle instead, the store would need a per-cell timestamp it deliberately does
 * not have.
 */
class ColumnMaskTest {

  private val size = 32

  @Test
  fun `a cell set is a cell read back, and nothing else moves`() {
    val mask = ColumnMask(size)

    assertTrue(mask.isEmpty)
    assertTrue(mask.set(3, 5))
    assertFalse(mask.set(3, 5), "setting an already-set cell reported a change")

    assertEquals(1, mask.count)
    assertTrue(mask[3, 5])
    assertFalse(mask[5, 3], "index order is transposed: (x, y) and (y, x) are the same cell")

    assertTrue(mask.clear(3, 5))
    assertFalse(mask.clear(3, 5), "clearing an already-clear cell reported a change")
    assertTrue(mask.isEmpty)
  }

  @Test
  fun `contains is false outside the chunk rather than throwing or wrapping`() {
    val mask = ColumnMask(size)
    mask.set(0, 0)

    assertTrue(mask.contains(0, 0))
    assertFalse(mask.contains(-1, 0))
    assertFalse(mask.contains(0, -1))
    assertFalse(mask.contains(size, 0))
    assertFalse(mask.contains(0, size))
  }

  /**
   * **The wire fixture.** `localY * size + localX`, bit `i` in byte `i / 8` counting from the least
   * significant. Written out by hand rather than derived from the class under test, or it would agree with
   * whatever the class happened to do.
   */
  @Test
  fun `bit order is byte i over eight, bit i mod eight, least significant first`() {
    val mask = ColumnMask(size)
    mask.set(0, 0)  // index 0  -> byte 0, bit 0
    mask.set(1, 0)  // index 1  -> byte 0, bit 1
    mask.set(7, 0)  // index 7  -> byte 0, bit 7
    mask.set(8, 0)  // index 8  -> byte 1, bit 0
    mask.set(0, 1)  // index 32 -> byte 4, bit 0

    val bytes = mask.toBytes()

    assertEquals(ColumnMask.byteLength(size), bytes.size)
    assertEquals(0b1000_0011, bytes[0].toInt() and 0xFF)
    assertEquals(0b0000_0001, bytes[1].toInt() and 0xFF)
    assertEquals(0b0000_0001, bytes[4].toInt() and 0xFF)
    assertEquals(0, bytes[2].toInt())
  }

  @Test
  fun `a mask survives a round trip through bytes`() {
    val mask = ColumnMask(size)
    for (i in 0 until size * size step 7) mask.set(i)

    val back = ColumnMask.fromBytes(size, mask.toBytes())

    assertEquals(mask.count, back.count)
    for (i in 0 until size * size) assertEquals(mask[i], back[i], "cell $i disagrees after a round trip")
  }

  /** A short array would decode as a mask with a clear tail, which reads as ground that is simply not burnt. */
  @Test
  fun `a payload of the wrong length is refused rather than padded`() {
    assertFailsWith<IllegalArgumentException> {
      ColumnMask.fromBytes(size, ByteArray(ColumnMask.byteLength(size) - 1))
    }
    assertFailsWith<IllegalArgumentException> {
      ColumnMask.fromBytes(size, ByteArray(ColumnMask.byteLength(size) + 1))
    }
  }

  @Test
  fun `erosion takes one ring off a disc per step`() {
    val mask = ColumnMask(size)
    val centre = 16
    val radius = 6
    for (y in 0 until size) {
      for (x in 0 until size) {
        val dx = x - centre
        val dy = y - centre
        if (dx * dx + dy * dy <= radius * radius) mask.set(x, y)
      }
    }

    val once = mask.eroded(1)
    val twice = mask.eroded(2)

    assertTrue(once.count < mask.count, "erosion removed nothing")
    assertTrue(twice.count < once.count, "the second step removed nothing")
    // The centre is the last thing to go, which is what makes a scar heal from its edges inward.
    assertTrue(twice[centre, centre], "erosion ate the middle of the disc before its rim")
  }

  @Test
  fun `a one-cell filament erodes away in a single step`() {
    val mask = ColumnMask(size)
    for (x in 4 until 20) mask.set(x, 10)

    assertTrue(mask.eroded(1).isEmpty, "a one-cell-wide line survived erosion")
  }

  @Test
  fun `eroding past the size of a scar leaves nothing rather than failing`() {
    val mask = ColumnMask(size)
    mask.set(5, 5)
    mask.set(5, 6)

    assertTrue(mask.eroded(50).isEmpty)
  }

  /** Erosion must not mutate the scar it is asked about - the stored mask is the durable one. */
  @Test
  fun `erosion returns a new mask and leaves the original alone`() {
    val mask = ColumnMask(size)
    for (x in 4 until 20) mask.set(x, 10)
    val before = mask.count

    mask.eroded(1)

    assertEquals(before, mask.count, "eroded() mutated the mask it was asked about")
  }

  @Test
  fun `or merges another mask in`() {
    val a = ColumnMask(size)
    val b = ColumnMask(size)
    a.set(1, 1)
    b.set(2, 2)
    b.set(1, 1)

    a.or(b)

    assertEquals(2, a.count, "or double-counted the cell both masks held")
    assertTrue(a[1, 1])
    assertTrue(a[2, 2])
  }
}
