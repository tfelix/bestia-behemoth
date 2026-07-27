package net.bestia.worldgen.vector

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StationTableTest {

  private val table = StationTable.Builder(5)
    .channel("width", doubleArrayOf(2.0, 4.0, 8.0, 16.0, 32.0))
    .channel("depth", doubleArrayOf(1.0, 1.0, 2.0, 3.0, 5.0))
    .build()

  @Test
  fun `interpolation passes exactly through the control values`() {
    val width = table.channel("width")

    for (i in 0 until 5) {
      assertEquals(table.valueAt(width, i), table.sample(width, i.toDouble()), 1e-12, "station $i")
    }
  }

  @Test
  fun `interpolation is continuous along the whole line`() {
    val width = table.channel("width")

    var previous = table.sample(width, 0.0)
    var u = 0.0
    while (u <= 4.0) {
      val current = table.sample(width, u)
      assertTrue(abs(current - previous) < 0.5, "width jumped by ${abs(current - previous)} at u=$u")
      previous = current
      u += 0.001
    }
  }

  @Test
  fun `sampling is clamped rather than extrapolated past the ends`() {
    val width = table.channel("width")

    // A river must not keep widening past its mouth because the spline overshot.
    assertEquals(2.0, table.sample(width, -12.0), 1e-12)
    assertEquals(32.0, table.sample(width, 99.0), 1e-12)
  }

  @Test
  fun `sampleInto agrees with per-channel sampling`() {
    val out = DoubleArray(table.channelCount)
    val width = table.channel("width")
    val depth = table.channel("depth")

    for (step in 0..40) {
      val u = step / 10.0
      table.sampleInto(u, out)
      assertEquals(table.sample(width, u), out[width], 1e-12)
      assertEquals(table.sample(depth, u), out[depth], 1e-12)
    }
  }

  @Test
  fun `sampling depends only on u, never on call order`() {
    val width = table.channel("width")

    val forward = (0..40).map { table.sample(width, it / 10.0) }
    val backward = (40 downTo 0).map { table.sample(width, it / 10.0) }.reversed()

    assertEquals(forward, backward)
  }

  @Test
  fun `a single station table is constant`() {
    val constant = StationTable.Builder(1).channel("c", doubleArrayOf(7.0)).build()

    assertEquals(7.0, constant.sample(0, 0.0), 1e-12)
    assertEquals(7.0, constant.sample(0, 5.0), 1e-12)
  }

  @Test
  fun `channel length must match the station count`() {
    assertFailsWith<IllegalArgumentException> {
      StationTable.Builder(3).channel("short", doubleArrayOf(1.0, 2.0))
    }
  }

  @Test
  fun `unknown channels fail loudly`() {
    assertFailsWith<IllegalArgumentException> { table.channel("discharge") }
  }

  @Test
  fun `duplicate channels are rejected`() {
    assertFailsWith<IllegalArgumentException> {
      StationTable.Builder(2)
        .channel("width", doubleArrayOf(1.0, 2.0))
        .channel("width", doubleArrayOf(3.0, 4.0))
    }
  }
}
