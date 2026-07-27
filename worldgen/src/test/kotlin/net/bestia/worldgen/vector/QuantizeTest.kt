package net.bestia.worldgen.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuantizeTest {

  @Test
  fun `values closer than the quantum compare equal`() {
    // This is the whole point: two nodes computing a sill position that differ in the last few bits
    // must reach the same decision about which side of it a column is on.
    val a = 128.0004
    val b = 128.0003

    assertTrue(a > b)
    assertEquals(0, Quantize.compare(a, b))
    assertFalse(Quantize.isAbove(a, b))
  }

  @Test
  fun `values further apart than the quantum still compare`() {
    assertTrue(Quantize.isAbove(128.002, 128.0))
    assertFalse(Quantize.isAbove(128.0, 128.002))
  }

  @Test
  fun `snapping is idempotent`() {
    val snapped = Quantize.snap(3.14159265)

    assertEquals(3.142, snapped, 1e-12)
    assertEquals(snapped, Quantize.snap(snapped), 0.0)
  }

  @Test
  fun `negative values snap symmetrically enough to stay monotonic`() {
    var previous = Quantize.toFixed(-10.0)
    var v = -10.0
    while (v <= 10.0) {
      val current = Quantize.toFixed(v)
      assertTrue(current >= previous, "quantization went backwards at $v")
      previous = current
      v += 0.0007
    }
  }
}
