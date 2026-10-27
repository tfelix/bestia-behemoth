package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bare patches in grassland: [SurfaceSampler.isBarePatchAt]. */
class BarePatchTest {

  private val region = CellRegion.world(16, 16, Resolution.KILOMETRE)

  private fun sampler(seed: Long = 0x5EEDL): SurfaceSampler {
    val cells = region.cellCount.toInt()

    return SurfaceSampler(
      biome = IntLayer(LayerId.BIOME, region, IntArray(cells) { Biome.GRASSLAND.ordinal }),
      soilDepth = FloatLayer(LayerId.SOIL_DEPTH, region, FloatArray(cells) { 1f }),
      waterLevel = FloatLayer(LayerId.WATER_LEVEL, region, FloatArray(cells) { Float.NaN }),
      lakeId = IntLayer(LayerId.LAKE_ID, region, IntArray(cells)),
      temperature = FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { 10f }),
      seed = seed
    )
  }

  /** Share of bare columns over an area, with a step wider than a patch so it crosses many of them. */
  private fun bareArea(sampler: SurfaceSampler, across: Int = 200): Double {
    var bare = 0
    for (iy in 0 until across) {
      for (ix in 0 until across) {
        if (sampler.isBarePatchAt(400.0 + ix * 31.7, 700.0 + iy * 29.3)) bare++
      }
    }
    return bare.toDouble() / (across * across)
  }

  @Test
  fun `about a tenth of grassland is bare`() {
    val area = bareArea(sampler())
    assertTrue(area in 0.05..0.16, "bare share $area")
  }

  @Test
  fun `bare ground comes in patches rather than as speckle`() {
    // Adjacent columns, one metre apart. At this share a per-column coin flip would make nearly nine in ten of
    // a bare column's neighbours turf; patches make far fewer of them turf, and only in the rim.
    val sampler = sampler()
    var bare = 0
    var differing = 0
    for (iy in 0 until 400) {
      for (ix in 0 until 400) {
        val x = 400.0 + ix
        val y = 700.0 + iy
        if (!sampler.isBarePatchAt(x, y)) continue
        bare++
        if (!sampler.isBarePatchAt(x + 1.0, y)) differing++
        if (!sampler.isBarePatchAt(x, y + 1.0)) differing++
      }
    }

    assertTrue(bare > 1_000, "too few bare columns to judge: $bare")
    val edge = differing / (2.0 * bare)
    assertTrue(edge < 0.35, "speckle: $edge of a bare column's neighbours are turf")
  }

  @Test
  fun `the rim is ragged, so the turf thins rather than stops`() {
    // Along a straight walk out of a patch, the bare/turf state flips more than once near the edge somewhere.
    val sampler = sampler()
    var raggedEdges = 0
    for (row in 0 until 200) {
      var flips = 0
      var last = sampler.isBarePatchAt(400.0, 700.0 + row * 13.0)
      for (ix in 1 until 600) {
        val here = sampler.isBarePatchAt(400.0 + ix, 700.0 + row * 13.0)
        if (here != last) flips++
        last = here
      }
      if (flips > 2) raggedEdges++
    }

    assertTrue(raggedEdges > 20, "only $raggedEdges of 200 walks crossed a ragged rim")
  }

  @Test
  fun `the patches are a pure function of world position and seed`() {
    val a = sampler()
    val b = sampler()
    val other = sampler(seed = 0xBADL)
    var differs = 0
    for (i in 0 until 5_000) {
      val x = 400.0 + i * 7.3
      val y = 700.0 + i * 3.1
      assertEquals(a.isBarePatchAt(x, y), b.isBarePatchAt(x, y), "two samplers disagree at ($x, $y)")
      if (a.isBarePatchAt(x, y) != other.isBarePatchAt(x, y)) differs++
    }

    assertTrue(differs > 100, "another seed put the patches in the same places")
  }
}
