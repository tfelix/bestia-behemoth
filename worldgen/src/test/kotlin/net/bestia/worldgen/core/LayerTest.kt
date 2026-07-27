package net.bestia.worldgen.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LayerTest {

  private val region = CellRegion.world(8, 8, Resolution.KILOMETRE)

  /** A ramp, so an interpolation error shows up as a value that is off rather than merely blurry. */
  private val ramp = FloatLayer(LayerId.ELEVATION, region, FloatArray(64) { (it % 8) * 100f })

  @Test
  fun `cell access is row major and clamps outside the region`() {
    assertEquals(0f, ramp[0, 0])
    assertEquals(700f, ramp[7, 0])
    assertEquals(300f, ramp[3, 5])

    // Clamping matters: a stage with a halo reads past the edge of the world map and must get the
    // edge value rather than an exception or a wrapped one.
    assertEquals(0f, ramp[-4, 0])
    assertEquals(700f, ramp[99, 3])
  }

  @Test
  fun `writing outside the region is rejected`() {
    assertFailsWith<IllegalArgumentException> { ramp[8, 0] = 1f }
  }

  @Test
  fun `both samplers reproduce the cell values at cell centres`() {
    for (cell in 0 until 8) {
      val centre = (cell + 0.5) * 1000.0

      assertEquals(ramp[cell, 4].toDouble(), ramp.sampleBilinear(centre, 4500.0), 1e-6, "cell $cell")
      assertEquals(ramp[cell, 4].toDouble(), ramp.sampleBicubic(centre, 4500.0), 1e-6, "cell $cell")
    }
  }

  @Test
  fun `bilinear interpolates linearly between cell centres`() {
    // Halfway between the centres of cells 2 (200) and 3 (300).
    assertEquals(250.0, ramp.sampleBilinear(3000.0, 4500.0), 1e-6)
  }

  @Test
  fun `bicubic is smooth where bilinear has a crease`() {
    // Bilinear on a ramp is exact, so compare curvature on a non-linear field instead.
    val bumpy = FloatLayer(
      LayerId.ELEVATION,
      region,
      FloatArray(64) { if ((it % 8) == 4) 100f else 0f }
    )

    fun secondDifference(sample: (Double) -> Double, x: Double) =
      sample(x - 20.0) - 2 * sample(x) + sample(x + 20.0)

    // Bilinear kinks at the cell *centre* it interpolates through - 4500 m for cell 4 - which is
    // what makes a coarse world map show visible facets when it is lifted into a chunk.
    val bilinearKink = abs(secondDifference({ bumpy.sampleBilinear(it, 4500.0) }, 4500.0))
    val bicubicKink = abs(secondDifference({ bumpy.sampleBicubic(it, 4500.0) }, 4500.0))

    assertTrue(bicubicKink < bilinearKink, "bicubic $bicubicKink was not smoother than $bilinearKink")
  }

  @Test
  fun `sampling is continuous across cell boundaries`() {
    var previous = ramp.sampleBicubic(500.0, 4500.0)
    var x = 500.0
    while (x <= 7500.0) {
      val current = ramp.sampleBicubic(x, 4500.0)
      assertTrue(abs(current - previous) < 5.0, "elevation jumped at x=$x")
      previous = current
      x += 10.0
    }
  }

  @Test
  fun `a layer must match the size of its region`() {
    assertFailsWith<IllegalArgumentException> {
      FloatLayer(LayerId.ELEVATION, region, FloatArray(10))
    }
  }

  @Test
  fun `expanding a region grows it on every side`() {
    val expanded = region.expanded(2)

    assertEquals(-2, expanded.minX)
    assertEquals(-2, expanded.minY)
    assertEquals(12, expanded.width)
    assertEquals(9, expanded.maxX)
  }

  @Test
  fun `a region converted to a coarser resolution covers the same world area`() {
    val coarse = region.at(Resolution.FOUR_KILOMETRE)

    assertEquals(2, coarse.width)
    assertEquals(2, coarse.height)
    assertEquals(region.toWorld(), coarse.toWorld())
  }

  @Test
  fun `a region converted to a finer resolution rounds outwards`() {
    val fine = region.at(Resolution(250.0))

    assertEquals(32, fine.width)
    assertEquals(region.toWorld(), fine.toWorld())
  }

  @Test
  fun `chunk keys are unique per coordinate`() {
    val keys = mutableSetOf<Long>()
    for (x in -8..8) {
      for (y in -8..8) {
        assertTrue(keys.add(ChunkPos(x, y).key()), "collision at ($x,$y)")
      }
    }
  }

  @Test
  fun `a column belongs to exactly one chunk and neighbours line up exactly`() {
    val config = WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)

    // The halo column of one chunk and the first interior column of the next are the same place,
    // and must be the same double - not merely close - or every seam check is meaningless.
    val halo = config.columnCenter(ChunkPos(3, 0), 32, 5)
    val interior = config.columnCenter(ChunkPos(4, 0), 0, 5)

    assertEquals(halo, interior)
  }
}
