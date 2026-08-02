package net.bestia.worldgen.vector

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

  // ---------------------------------------------------------------------------------------------
  // Periodic tables, for rings. Everything above is the open case and is deliberately untouched: it
  // is the guard that adding the flag changed nothing for the callers that do not pass it.
  // ---------------------------------------------------------------------------------------------

  /** Four stations round a ring. Deliberately not symmetric, so a rotation by one is detectable. */
  private val ring = StationTable.Builder(4, periodic = true)
    .channel("depth", doubleArrayOf(1.0, 5.0, 9.0, 3.0))
    .build()

  @Test
  fun `a periodic table has as many segments as stations`() {
    assertEquals(4, ring.segmentCount, "a ring's last station joins back to its first")
    assertEquals(4, StationTable.Builder(5).channel("c") { 0.0 }.build().segmentCount, "an open one does not")
  }

  @Test
  fun `a periodic table wraps rather than clamping`() {
    val depth = ring.channel("depth")

    for (i in 0 until 4) {
      assertEquals(ring.valueAt(depth, i), ring.sample(depth, i.toDouble()), 1e-12, "station $i")
      assertEquals(
        ring.sample(depth, i.toDouble()),
        ring.sample(depth, i + 4.0),
        1e-12,
        "one lap later"
      )
      assertEquals(
        ring.sample(depth, i.toDouble()),
        ring.sample(depth, i - 4.0),
        1e-12,
        "one lap earlier"
      )
    }
    // A negative station parameter is as legal as any other and must not fold onto station 0.
    assertEquals(ring.sample(depth, 3.5), ring.sample(depth, -0.5), 1e-12)
  }

  @Test
  fun `a periodic table is smooth across the seam, not merely continuous`() {
    val depth = ring.channel("depth")

    // The failure a clamped-neighbour implementation gives: the value still matches at the seam because
    // both sides read station 0, but the *slope* does not, so there is a kink at exactly one vertex.
    // Compare the one-sided derivatives just either side of station 0.
    val h = 1e-5
    val before = (ring.sample(depth, 0.0) - ring.sample(depth, -h)) / h
    val after = (ring.sample(depth, h) - ring.sample(depth, 0.0)) / h

    assertTrue(
      abs(before - after) < 1e-3,
      "slope jumped from $before to $after across the seam"
    )

    // And the same must hold at an ordinary station, so the assertion above is not vacuously loose.
    val innerBefore = (ring.sample(depth, 2.0) - ring.sample(depth, 2.0 - h)) / h
    val innerAfter = (ring.sample(depth, 2.0 + h) - ring.sample(depth, 2.0)) / h
    assertTrue(abs(innerBefore - innerAfter) < 1e-3, "slope at an ordinary station")
  }

  @Test
  fun `the segment from the last station back to the first carries real values`() {
    val depth = ring.channel("depth")

    // An open table over the same four numbers has nothing between u=3 and u=4 - it clamps. A periodic
    // one interpolates 3.0 back round to 1.0, and must actually move while doing so.
    val mid = ring.sample(depth, 3.5)
    assertTrue(mid > 1.0 && mid < 5.0, "the wrap segment sampled $mid, which is not between 3 and 1")

    val open = StationTable.Builder(4).channel("depth", doubleArrayOf(1.0, 5.0, 9.0, 3.0)).build()
    assertEquals(3.0, open.sample(open.channel("depth"), 3.5), 1e-12, "the open table clamps here")
  }

  @Test
  fun `periodic is part of the table's identity`() {
    assertTrue(ring.periodic)
    assertFalse(table.periodic)
  }

  @Test
  fun `a periodic table needs enough stations to be a closed curve`() {
    assertFailsWith<IllegalArgumentException> { StationTable.Builder(2, periodic = true) }
  }
}
