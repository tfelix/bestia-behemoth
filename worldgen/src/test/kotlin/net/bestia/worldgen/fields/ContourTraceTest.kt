package net.bestia.worldgen.fields

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The marching-squares tracer.
 *
 * The properties worth asserting are the ones a picture would not show: that a ring comes back **closed**
 * rather than as an open chain missing its last edge, that a line running off the grid comes back as **one**
 * open chain rather than two halves, that the vertices sit where the *field* says and not on the grid the
 * boolean was sampled on, and that an inland hollow the boolean excludes contributes nothing however far
 * below the iso level its field values are.
 */
class ContourTraceTest {

  private val width = 24
  private val height = 24

  /** A disc of "inside" cells, with a field that is negative inside it and positive outside. */
  private fun disc(cx: Double, cy: Double, radius: Double): Pair<BooleanArray, DoubleArray> {
    val inside = BooleanArray(width * height)
    val field = DoubleArray(width * height)

    for (y in 0 until height) {
      for (x in 0 until width) {
        val d = Math.hypot(x - cx, y - cy) - radius
        inside[y * width + x] = d < 0.0
        field[y * width + x] = d
      }
    }

    return inside to field
  }

  @Test
  fun `a disc traces one closed ring`() {
    val (inside, field) = disc(12.0, 12.0, 7.0)
    val contours = ContourTrace.trace(width, height, inside, field)

    assertEquals(1, contours.size, "a single disc should trace a single boundary")
    assertTrue(contours[0].closed, "a boundary entirely inside the grid should come back closed")
    assertTrue(contours[0].size > 20, "the ring is too coarse to be the disc, got ${contours[0].size} vertices")
  }

  @Test
  fun `the ring sits on the field and not on the grid`() {
    val radius = 7.3
    val (inside, field) = disc(12.0, 12.0, radius)
    val ring = ContourTrace.trace(width, height, inside, field).single()

    // Every vertex within a tenth of a cell of the true circle. A tracer that put its vertices at cell centres
    // or at edge midpoints would be out by up to half a cell, which is what this number is chosen to exclude.
    for (i in 0 until ring.size) {
      val error = abs(Math.hypot(ring.xs[i] - 12.0, ring.ys[i] - 12.0) - radius)
      assertTrue(error < 0.1, "vertex $i is ${"%.3f".format(error)} cells off the circle")
    }
  }

  @Test
  fun `a region running off the grid traces one open chain`() {
    // A half-plane: everything left of x = 9.4 is inside, so the boundary is a straight line from the bottom
    // edge to the top edge and cannot close.
    val inside = BooleanArray(width * height)
    val field = DoubleArray(width * height)

    for (y in 0 until height) {
      for (x in 0 until width) {
        field[y * width + x] = x - 9.4
        inside[y * width + x] = x < 9.4
      }
    }

    val contours = ContourTrace.trace(width, height, inside, field)

    assertEquals(1, contours.size, "the boundary is one line, not two halves of one")
    assertTrue(!contours[0].closed, "a line that runs off the grid is not a closed ring")
    assertEquals(height, contours[0].size, "the chain should span every row")

    for (i in 0 until contours[0].size) {
      assertTrue(abs(contours[0].xs[i] - 9.4) < 0.001, "the line should stand at x = 9.4")
    }
  }

  @Test
  fun `an excluded hollow contributes nothing`() {
    // Two discs by the field, but only one of them is marked inside - which is exactly the shape of an inland
    // basin below sea level that is not connected to the ocean. The tracer must follow the boolean.
    val (inside, field) = disc(7.0, 7.0, 4.0)

    for (y in 0 until height) {
      for (x in 0 until width) {
        val hollow = Math.hypot(x - 17.0, y - 17.0) - 3.0
        if (hollow < 0.0) field[y * width + x] = hollow
      }
    }

    val contours = ContourTrace.trace(width, height, inside, field)

    assertEquals(1, contours.size, "the unconnected hollow should not become a coastline")

    for (i in 0 until contours[0].size) {
      assertTrue(
        Math.hypot(contours[0].xs[i] - 7.0, contours[0].ys[i] - 7.0) < 6.0,
        "the traced ring should belong to the connected disc"
      )
    }
  }

  @Test
  fun `two separate regions trace two rings`() {
    val inside = BooleanArray(width * height)
    val field = DoubleArray(width * height)

    for (y in 0 until height) {
      for (x in 0 until width) {
        val a = Math.hypot(x - 6.0, y - 6.0) - 3.5
        val b = Math.hypot(x - 17.0, y - 17.0) - 3.5
        val d = minOf(a, b)
        field[y * width + x] = d
        inside[y * width + x] = d < 0.0
      }
    }

    val contours = ContourTrace.trace(width, height, inside, field)

    assertEquals(2, contours.size)
    assertTrue(contours.all { it.closed }, "both islands should come back closed")
  }

  @Test
  fun `tracing is a function of the input and not of iteration order`() {
    val (inside, field) = disc(12.0, 12.0, 7.0)

    val first = ContourTrace.trace(width, height, inside, field)
    val second = ContourTrace.trace(width, height, inside, field)

    assertEquals(first.size, second.size)
    for (c in first.indices) {
      assertTrue(first[c].xs.contentEquals(second[c].xs), "contour $c moved between runs")
      assertTrue(first[c].ys.contentEquals(second[c].ys), "contour $c moved between runs")
    }
  }
}
