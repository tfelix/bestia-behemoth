package net.bestia.worldgen.viewer

import net.bestia.worldgen.vector.Aabb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ViewportTest {

  private val view = Viewport(centerX = 1000.0, centerY = 500.0, metresPerPixel = 4.0, widthPx = 200, heightPx = 100)

  @Test
  fun `screen and world coordinates round trip at pixel centres`() {
    for (px in intArrayOf(0, 1, 77, 199)) {
      assertEquals(px.toDouble(), view.screenX(view.worldX(px)), 1e-9, "column $px")
    }
    for (py in intArrayOf(0, 1, 43, 99)) {
      assertEquals(py.toDouble(), view.screenY(view.worldY(py)), 1e-9, "row $py")
    }
  }

  @Test
  fun `north is up`() {
    // The single most common way to get a map subtly wrong, and invisible unless asserted.
    assertTrue(view.worldY(0) > view.worldY(99), "row 0 must be further north than the last row")
    assertTrue(view.worldX(0) < view.worldX(199))
  }

  @Test
  fun `the view is centred on its centre`() {
    assertEquals(view.centerX, view.worldX(99) + view.metresPerPixel / 2.0, 1e-9)
    assertEquals(view.centerY, view.worldY(49) - view.metresPerPixel / 2.0, 1e-9)
  }

  @Test
  fun `zooming keeps the world position under the cursor fixed`() {
    // What makes it possible to follow a river upstream instead of losing it at every wheel notch.
    for (factor in doubleArrayOf(1.25, 4.0, 0.5)) {
      for (px in intArrayOf(0, 37, 199)) {
        for (py in intArrayOf(0, 61, 99)) {
          val before = view.worldX(px) to view.worldY(py)
          val zoomed = view.zoomedAt(px, py, factor)

          assertEquals(before.first, zoomed.worldX(px), 1e-9, "x at ($px,$py) x$factor")
          assertEquals(before.second, zoomed.worldY(py), 1e-9, "y at ($px,$py) x$factor")
        }
      }
    }
  }

  @Test
  fun `setting the scale lands on it exactly and keeps the cursor fixed`() {
    // Exactly, not nearly: at voxel scale a scale that is off by a rounding error turns every voxel into a
    // ragged run of one-or-two pixels, which reads as a materialiser producing uneven columns.
    for (target in doubleArrayOf(1.0, 0.3, 7.0)) {
      for (px in intArrayOf(0, 37, 199)) {
        for (py in intArrayOf(0, 61, 99)) {
          val before = view.worldX(px) to view.worldY(py)
          val scaled = view.scaledAt(px, py, target)

          assertEquals(target, scaled.metresPerPixel, 0.0, "scale at ($px,$py) -> $target")
          assertEquals(before.first, scaled.worldX(px), 1e-9, "x at ($px,$py) -> $target")
          assertEquals(before.second, scaled.worldY(py), 1e-9, "y at ($px,$py) -> $target")
        }
      }
    }
  }

  @Test
  fun `a pixel spans exactly one voxel at voxel scale`() {
    // The property the whole voxel view rests on: pixel n and pixel n+1 are adjacent columns, one metre apart.
    val voxel = view.scaledAt(100, 50, 1.0)

    assertEquals(1.0, voxel.worldX(1) - voxel.worldX(0), 0.0)
    assertEquals(1.0, voxel.worldY(0) - voxel.worldY(1), 0.0)
    assertEquals(200.0, voxel.bounds.width, 1e-9)
  }

  @Test
  fun `zooming in shows less of the world`() {
    val zoomed = view.zoomedAtCenter(2.0)

    assertEquals(2.0, zoomed.metresPerPixel, 1e-12)
    assertTrue(zoomed.bounds.width < view.bounds.width)
    assertEquals(view.centerX, zoomed.centerX, 1e-9)
    assertEquals(view.centerY, zoomed.centerY, 1e-9)
  }

  @Test
  fun `dragging moves the world with the pointer`() {
    val dragged = view.pannedByPixels(10, 6)

    // Whatever was under (50,50) is now under (60,56).
    assertEquals(view.worldX(50), dragged.worldX(60), 1e-9)
    assertEquals(view.worldY(50), dragged.worldY(56), 1e-9)
  }

  @Test
  fun `fit shows the whole area`() {
    val area = Aabb(-5000.0, 2000.0, 15000.0, 9000.0)
    val fitted = Viewport.fit(area, 400, 300)

    assertTrue(fitted.bounds.minX <= area.minX && fitted.bounds.maxX >= area.maxX, "${fitted.bounds}")
    assertTrue(fitted.bounds.minY <= area.minY && fitted.bounds.maxY >= area.maxY, "${fitted.bounds}")
    assertEquals((area.minX + area.maxX) / 2.0, fitted.centerX, 1e-9)
  }

  @Test
  fun `a degenerate viewport is rejected`() {
    assertFailsWith<IllegalArgumentException> { Viewport(0.0, 0.0, 0.0, 10, 10) }
    assertFailsWith<IllegalArgumentException> { Viewport(0.0, 0.0, 1.0, 0, 10) }
  }
}
