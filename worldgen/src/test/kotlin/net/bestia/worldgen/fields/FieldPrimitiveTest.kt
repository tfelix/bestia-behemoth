package net.bestia.worldgen.fields

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.vector.Aabb
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FieldPrimitiveTest {

  @Test
  fun `the distance transform agrees with brute force`() {
    // The whole reason for the separable algorithm over a chamfer approximation is exactness, so it is
    // worth actually checking rather than trusting.
    val width = 23
    val height = 17
    val seeds = setOf(0 to 0, 7 to 3, 22 to 16, 11 to 9)

    val grid = DistanceTransform.euclidean(width, height) { x, y -> (x to y) in seeds }

    for (y in 0 until height) {
      for (x in 0 until width) {
        val expected = seeds.minOf { (sx, sy) ->
          sqrt(((x - sx) * (x - sx) + (y - sy) * (y - sy)).toDouble())
        }
        assertEquals(expected, grid[x, y], 1e-9, "($x,$y)")
      }
    }
  }

  @Test
  fun `the distance transform is not biased along the diagonal`() {
    // A chamfer transform under-reports diagonal distances by a few per cent, and a climate field driven
    // by one acquires faint diagonal banding that looks like a bug in the noise.
    val grid = DistanceTransform.euclidean(41, 41) { x, y -> x == 20 && y == 20 }

    val alongAxis = grid[30, 20]
    val alongDiagonal = grid[27, 27]

    assertEquals(10.0, alongAxis, 1e-9)
    assertEquals(sqrt(2.0) * 7.0, alongDiagonal, 1e-9)
  }

  @Test
  fun `an empty mask leaves every cell unreachable rather than at zero`() {
    val grid = DistanceTransform.euclidean(8, 8) { _, _ -> false }
    assertTrue(grid.data.all { it == Double.MAX_VALUE }, "an empty mask must not read as all-zero")
  }

  @Test
  fun `poisson disk samples respect the minimum distance and fill the area`() {
    val bounds = Aabb(0.0, 0.0, 1000.0, 600.0)
    val points = PoissonDisk.sample(bounds, minDistance = 40.0, rng = GenRng(7L))

    assertTrue(points.size > 100, "only ${points.size} points in 600 000 square metres at 40 m spacing")

    for (i in points.indices) {
      assertTrue(bounds.contains(points[i].x, points[i].y), "${points[i]} is outside the bounds")
      for (j in i + 1 until points.size) {
        assertTrue(
          points[i].distanceTo(points[j]) >= 40.0 - 1e-9,
          "${points[i]} and ${points[j]} are ${points[i].distanceTo(points[j])} apart"
        )
      }
    }
  }

  @Test
  fun `poisson disk sampling is a pure function of its stream`() {
    val bounds = Aabb(0.0, 0.0, 400.0, 400.0)
    val once = PoissonDisk.sample(bounds, 30.0, GenRng(99L))
    val twice = PoissonDisk.sample(bounds, 30.0, GenRng(99L))

    assertEquals(once, twice)
  }

  @Test
  fun `the point index finds the same two nearest points as brute force`() {
    val bounds = Aabb(-500.0, -500.0, 1500.0, 900.0)
    val points = PoissonDisk.sample(bounds, 55.0, GenRng(3L))
    val index = PointIndex(points, bounds)

    val rng = GenRng(11L)
    val out = DoubleArray(4)

    repeat(400) {
      val x = rng.nextDouble(bounds.minX, bounds.maxX)
      val y = rng.nextDouble(bounds.minY, bounds.maxY)
      index.nearestTwo(x, y, out)

      val sorted = points.indices.sortedBy { points[it].distanceSquaredTo(net.bestia.worldgen.vector.Vec2d(x, y)) }
      assertEquals(sorted[0], out[0].toInt(), "nearest at ($x,$y)")
      assertEquals(sorted[1], out[1].toInt(), "second nearest at ($x,$y)")
    }
  }

  @Test
  fun `the heap pops in key order and breaks ties on the value`() {
    // The tie-break is a correctness requirement, not a nicety: a flat plain is thousands of cells at
    // exactly the same elevation, and an unstable order there means two runs route them differently.
    val heap = DoubleIntHeap()
    heap.push(3.0, 30)
    heap.push(1.0, 11)
    heap.push(1.0, 10)
    heap.push(2.0, 20)
    heap.push(1.0, 12)

    assertEquals(listOf(10, 11, 12, 20, 30), List(5) { heap.pop() })
    assertTrue(heap.isEmpty)
  }

  @Test
  fun `the heap survives more entries than its initial capacity`() {
    val heap = DoubleIntHeap(initialCapacity = 2)
    val values = (0 until 500).shuffled(java.util.Random(4))
    for (v in values) heap.push(v.toDouble(), v)

    for (expected in 0 until 500) {
      assertEquals(expected, heap.pop())
    }
  }

  @Test
  fun `a table is interpolated between its samples and clamped outside them`() {
    val table = doubleArrayOf(0.0, 10.0, 30.0)

    assertEquals(0.0, Tables.linear(table, 0.0), 1e-12)
    assertEquals(5.0, Tables.linear(table, 0.5), 1e-12)
    assertEquals(10.0, Tables.linear(table, 1.0), 1e-12)
    assertEquals(20.0, Tables.linear(table, 1.5), 1e-12)
    assertEquals(0.0, Tables.linear(table, -4.0), 1e-12)
    assertEquals(30.0, Tables.linear(table, 9.0), 1e-12)
  }

  @Test
  fun `a table read at a spacing does not become a staircase`() {
    // The bug this guards against: reading a regularly sampled table with toInt() turns it into a
    // staircase whose tread is the sample interval, and a spline through those values then rings at that
    // pitch - which shows up in the world as regular scalloping at a spacing that matches nothing.
    val table = DoubleArray(10) { it * 100.0 }

    var previous = Double.NEGATIVE_INFINITY
    var s = 0.0
    while (s <= 900.0) {
      val value = Tables.atSpacing(table, s, spacing = 100.0)
      assertTrue(value > previous, "value went flat or backwards at s=$s")
      previous = value
      s += 7.0
    }
  }

  @Test
  fun `a grid gradient is symmetric on a uniform slope`() {
    val grid = Grid(9, 9) { x, _ -> x * 5.0 }

    for (y in 1 until 8) {
      for (x in 1 until 8) {
        assertEquals(0.05, grid.gradient(x, y, metresPerCell = 100.0), 1e-12, "($x,$y)")
      }
    }
  }

  @Test
  fun `blurring conserves the mean`() {
    val grid = Grid(16, 16) { x, y -> ((x * 7 + y * 13) % 11).toDouble() }
    val before = grid.mean()

    grid.blur(3)

    assertTrue(abs(grid.mean() - before) < 0.35, "mean drifted from $before to ${grid.mean()}")
  }

  @Test
  fun `a degenerate grid is rejected`() {
    assertFailsWith<IllegalArgumentException> { Grid(0, 4) }
    assertFailsWith<IllegalArgumentException> { Grid(4, 4, DoubleArray(3)) }
  }
}
