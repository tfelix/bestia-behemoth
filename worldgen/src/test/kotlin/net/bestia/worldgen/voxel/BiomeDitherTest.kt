package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The biome-boundary dither in [SurfaceSampler].
 *
 * Two properties, and the second is the one that matters for correctness rather than for looks. The dither has
 * to actually mix the pair in proportion to the blend weight, and it has to be a **pure function of world
 * position** - if it were not, two chunks either side of a border would disagree about a column they share, and
 * that is exactly the class of seam the vector tier exists to avoid. The chunk seam check compares heights
 * rather than blocks, so it would not catch this one.
 *
 * Built on synthetic layers rather than a generated world: the interesting cases are a perfect tie and total
 * certainty, and a real world contains neither on demand.
 */
class BiomeDitherTest {

  private val region = CellRegion.world(16, 16, Resolution.KILOMETRE)

  private fun sampler(
    winner: Biome,
    runnerUp: Int,
    confidence: Float,
    boundaryJitter: Double = 0.0
  ): SurfaceSampler {
    val cells = region.cellCount.toInt()

    return SurfaceSampler(
      biome = IntLayer(LayerId.BIOME, region, IntArray(cells) { winner.ordinal }),
      soilDepth = FloatLayer(LayerId.SOIL_DEPTH, region, FloatArray(cells) { 1f }),
      waterLevel = FloatLayer(LayerId.WATER_LEVEL, region, FloatArray(cells) { Float.NaN }),
      lakeId = IntLayer(LayerId.LAKE_ID, region, IntArray(cells)),
      temperature = FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { 10f }),
      seed = 0x5EEDL,
      // No warp, so the test measures the dither rather than the warp's displacement of the lookup.
      boundaryJitter = boundaryJitter,
      secondaryBiome = IntLayer(LayerId.BIOME_SECONDARY, region, IntArray(cells) { runnerUp }),
      biomeConfidence = FloatLayer(LayerId.BIOME_CONFIDENCE, region, FloatArray(cells) { confidence })
    )
  }

  /**
   * Area fraction of the runner-up, over a two-dimensional patch of ground.
   *
   * **Sampled as an area rather than along a line**, which matters now that the mixing field is spatially
   * coherent rather than a per-column hash: consecutive samples along a line fall inside the same patch, so a
   * line walk measures one blob's neighbourhood repeatedly and converges to nothing in particular. The step is
   * wider than [SurfaceSampler.PATCH_WAVELENGTH] so the grid crosses many independent patches.
   */
  private fun runnerUpArea(sampler: SurfaceSampler, runnerUp: Biome, across: Int = 180): Double {
    var hits = 0
    for (iy in 0 until across) {
      for (ix in 0 until across) {
        val x = 400.0 + ix * 19.7
        val y = 700.0 + iy * 23.3
        if (sampler.biomeAt(x, y) == runnerUp) hits++
      }
    }
    return hits.toDouble() / (across * across)
  }

  @Test
  fun `full confidence never yields the runner-up`() {
    val area = runnerUpArea(sampler(Biome.TAIGA, Biome.TUNDRA.ordinal, 1.0f), Biome.TUNDRA)
    assertEquals(0.0, area, 0.0, "a certain cell was dithered anyway")
  }

  @Test
  fun `confidence at or above the cutoff never yields the runner-up`() {
    // The bound that keeps this a boundary effect. Without it the mixing follows BIOME_CONFIDENCE's own scale,
    // which measurement showed sits near zero across the whole world - see SurfaceSampler.biomeAt.
    for (clarity in listOf(SurfaceSampler.DITHER_CUTOFF, 0.3, 0.6, 1.0)) {
      val area = runnerUpArea(sampler(Biome.TAIGA, Biome.TUNDRA.ordinal, clarity.toFloat()), Biome.TUNDRA)
      assertEquals(0.0, area, 0.0, "confidence $clarity is at or above the cutoff but still mixed")
    }
  }

  @Test
  fun `a perfect tie splits the ground evenly`() {
    // The one exactly-known point on the curve, and it is exact for a reason rather than by calibration: half
    // the noise field lies below its own midpoint. Two prototypes that scored identically have equal claim, and
    // the un-halved form `1 - confidence` would instead hand a dead tie entirely to the *loser*.
    val area = runnerUpArea(sampler(Biome.TAIGA, Biome.TUNDRA.ordinal, 0.0f), Biome.TUNDRA)
    assertTrue(
      abs(area - 0.5) < 0.03,
      "a tie gave the runner-up ${"%.3f".format(Locale.ROOT, area)} of the ground, expected about half"
    )
  }

  @Test
  fun `the runner-up area falls monotonically as confidence rises`() {
    // Monotone rather than proportional. The area is a function of the noise field's value distribution, which
    // is not uniform, so there is no linear law to assert - and asserting one against measured constants would
    // pin the test to the octave count rather than to the behaviour. Ordering plus the two endpoints is the
    // real contract: mixing is strongest at a tie and gone by the cutoff.
    var previous = Double.MAX_VALUE
    for (clarity in listOf(0.0, 0.05, 0.10, 0.15, 0.20)) {
      val area = runnerUpArea(sampler(Biome.TAIGA, Biome.TUNDRA.ordinal, clarity.toFloat()), Biome.TUNDRA)
      assertTrue(
        area <= previous + 0.01,
        "confidence $clarity gave a larger runner-up area ($area) than the step below it ($previous)"
      )
      previous = area
    }
    assertEquals(0.0, previous, 0.0, "the last step is the cutoff and should mix nothing")
  }

  @Test
  fun `the mixing arrives in patches rather than as speckle`() {
    // The property the first implementation lacked, and the only one that says *why* this uses a noise field.
    // A per-column hash gives every column an independent outcome, so at an even split about half of a
    // column's four neighbours differ from it. A coherent field gives contiguous blobs, so far fewer do.
    val sampler = sampler(Biome.TAIGA, Biome.TUNDRA.ordinal, 0.0f)

    var differing = 0
    var compared = 0
    for (iy in 0 until 120) {
      for (ix in 0 until 120) {
        // One metre apart: adjacent voxel columns, which is the scale the speckle appeared at.
        val x = 400.0 + ix
        val y = 700.0 + iy
        val here = sampler.biomeAt(x, y)
        for ((dx, dy) in listOf(1.0 to 0.0, 0.0 to 1.0)) {
          if (sampler.biomeAt(x + dx, y + dy) != here) differing++
          compared++
        }
      }
    }

    val boundaryRate = differing.toDouble() / compared
    assertTrue(
      boundaryRate < 0.10,
      "${"%.1f".format(Locale.ROOT, 100 * boundaryRate)}% of neighbouring columns differ, so the mixing is " +
          "speckle rather than patches - a per-column hash gives about 50%"
    )
  }

  @Test
  fun `the sentinel is never treated as a biome`() {
    // `Biome.of` coerces, so the sentinel would come back as the last enum entry - CLIFF. A cell with no
    // runner-up must stay the winner however low its confidence claims to be.
    val sampler = sampler(Biome.TAIGA, LayerId.NO_SECONDARY, 0.0f)

    for (n in 0 until 5_000) {
      val got = sampler.biomeAt(400.0 + n * 7.331, 700.0 + n * 3.977)
      assertEquals(
        Biome.TAIGA, got,
        "a cell with the sentinel came back as $got - Biome.of coerced -1 into the enum"
      )
    }
  }

  @Test
  fun `the dither is a pure function of world position`() {
    // The seam argument. Two samplers built identically from the same seed must agree column for column,
    // because in the real pipeline the "two samplers" are two chunk workers asking about a shared column.
    val a = sampler(Biome.TAIGA, Biome.TUNDRA.ordinal, 0.3f, boundaryJitter = 420.0)
    val b = sampler(Biome.TAIGA, Biome.TUNDRA.ordinal, 0.3f, boundaryJitter = 420.0)

    for (n in 0 until 10_000) {
      val x = 1_000.0 + n * 1.37
      val y = 2_000.0 + n * 0.91
      assertEquals(
        a.biomeAt(x, y), b.biomeAt(x, y),
        "two samplers disagree about the column at ($x, $y)"
      )
    }
  }

  @Test
  fun `repeated sampling of one column is stable`() {
    // The same claim from the other side: a chunk regenerated after a cache eviction must produce the same
    // ground, so the roll cannot depend on call order or on any accumulated state.
    val sampler = sampler(Biome.TAIGA, Biome.TUNDRA.ordinal, 0.2f, boundaryJitter = 420.0)

    for (n in 0 until 200) {
      val x = 3_140.0 + n * 11.0
      val y = 5_920.0 + n * 13.0
      val first = sampler.biomeAt(x, y)
      repeat(4) {
        assertEquals(first, sampler.biomeAt(x, y), "column ($x, $y) changed between reads")
      }
    }
  }

  @Test
  fun `a sampler with no secondary layers behaves as it did before the pair existed`() {
    // The partial-pipeline path. A stage test that stops before BiomeStage has neither layer, and it should get
    // the plain warped lookup rather than an exception.
    val cells = region.cellCount.toInt()
    val plain = SurfaceSampler(
      biome = IntLayer(LayerId.BIOME, region, IntArray(cells) { Biome.TAIGA.ordinal }),
      soilDepth = FloatLayer(LayerId.SOIL_DEPTH, region, FloatArray(cells) { 1f }),
      waterLevel = FloatLayer(LayerId.WATER_LEVEL, region, FloatArray(cells) { Float.NaN }),
      lakeId = IntLayer(LayerId.LAKE_ID, region, IntArray(cells)),
      temperature = FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { 10f }),
      seed = 0x5EEDL
    )

    for (n in 0 until 1_000) {
      assertEquals(Biome.TAIGA, plain.biomeAt(400.0 + n * 7.331, 700.0 + n * 3.977))
    }
  }
}
