package net.bestia.worldgen.geo

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.fields.IntGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `BoundaryTracer` had no dedicated test anywhere in the suite - only indirect coverage through full-world
 * builds, which check emergent properties (ore near a boundary, a fault feature existing) and would not
 * notice a wrong edge orientation, a dropped short run, or two boundaries bleeding into one trace. These
 * build small, hand-drawn plate rasters where the right answer is known exactly.
 */
class BoundaryTracerTest {

  private val region = CellRegion.world(20, 20, Resolution.KILOMETRE)

  private fun grid(width: Int, height: Int, plateAt: (Int, Int) -> Int): IntGrid {
    val data = IntArray(width * height)
    for (y in 0 until height) {
      for (x in 0 until width) {
        data[y * width + x] = plateAt(x, y)
      }
    }
    return IntGrid(width, height, data)
  }

  @Test
  fun `a single plate has no boundary at all`() {
    val plates = grid(20, 20) { _, _ -> 0 }
    assertTrue(BoundaryTracer.trace(plates, region).isEmpty())
  }

  @Test
  fun `a straight vertical boundary is traced as one run along it`() {
    // Plate 0 west of x=10, plate 1 east of it - a clean, known-shape boundary to check both the geometry
    // and the plate-id bookkeeping against.
    val plates = grid(20, 20) { x, _ -> if (x < 10) 0 else 1 }

    val traces = BoundaryTracer.trace(plates, region, minVertices = 6, smoothing = 0)

    assertEquals(1, traces.size, "expected exactly one traced boundary")
    val trace = traces.single()
    assertEquals(0, trace.plateA)
    assertEquals(1, trace.plateB)

    // The boundary sits on the shared edge, at world x = 10 km, for the full 20 km height.
    for (point in trace.line.points) {
      assertEquals(10_000.0, point.x, 1e-6, "boundary point strayed off the dividing line: $point")
    }
    val ys = trace.line.points.map { it.y }
    assertTrue(ys.max() - ys.min() > 15_000.0, "boundary should run nearly the full height of the region")
  }

  @Test
  fun `a boundary shorter than minVertices is dropped entirely`() {
    // Plate 1 occupies only a two-cell sliver in a corner, so the 0/1 boundary is three edge points -
    // short of the default minVertices=6, and the whole pair must be dropped before it ever reaches chaining.
    val plates = grid(20, 20) { x, y -> if (x == 0 && y < 2) 1 else 0 }

    assertTrue(BoundaryTracer.trace(plates, region).isEmpty())

    // The same raster, with the threshold lowered to fit its three points, does produce a trace - confirming
    // the emptiness above is the threshold working, not some unrelated failure to find the boundary at all.
    val lowered = BoundaryTracer.trace(plates, region, minVertices = 3)
    assertEquals(1, lowered.size)
  }

  @Test
  fun `three plates meeting produce three separate boundary traces, not one`() {
    // A T-junction: plate 0 (bottom-left), plate 1 (bottom-right), plate 2 (the whole top half). Three
    // distinct pairs share the grid, and each has to stay its own Trace rather than merging into one run
    // or losing one of the three to the chaining walk.
    val plates = grid(20, 20) { x, y ->
      when {
        y >= 10 -> 2
        x < 10 -> 0
        else -> 1
      }
    }

    val traces = BoundaryTracer.trace(plates, region, minVertices = 6, smoothing = 0)
    val pairs = traces.map { setOf(it.plateA, it.plateB) }.toSet()

    assertEquals(3, traces.size, "expected one trace per plate pair: $traces")
    assertEquals(setOf(setOf(0, 1), setOf(0, 2), setOf(1, 2)), pairs)
  }

  @Test
  fun `tracing the same raster twice gives identical results`() {
    val plates = grid(20, 20) { x, y -> if (x + y < 20) 0 else 1 }

    val first = BoundaryTracer.trace(plates, region)
    val second = BoundaryTracer.trace(plates, region)

    assertEquals(first.size, second.size)
    for ((a, b) in first.zip(second)) {
      assertEquals(a.plateA, b.plateA)
      assertEquals(a.plateB, b.plateB)
      assertEquals(a.line.points, b.line.points)
    }
  }
}
