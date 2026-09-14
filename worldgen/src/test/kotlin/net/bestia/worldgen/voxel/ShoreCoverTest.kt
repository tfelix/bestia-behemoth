package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shore band in [SurfaceSampler].
 *
 * It exists because two of the cover rules were isolines of elevation - the material changed exactly at the
 * waterline and again exactly sixty metres below it - and a comparison against a constant on a smooth height
 * field draws a clean curve across ground that has no other clean curve in it.
 *
 * What is asserted here is what a picture cannot be: that the transition **has width** rather than being a line
 * moved somewhere else, that the width **follows the slope**, that a cliff coast is left alone for the
 * materialiser's own bare-rock rule to answer, and that the whole thing is a **pure function of world position**
 * - two chunks either side of a shoreline evaluate it independently and have to agree column for column.
 */
class ShoreCoverTest {

  private val region = CellRegion.world(16, 16, Resolution.KILOMETRE)

  private fun sampler(temperature: Float = 15f): SurfaceSampler {
    val cells = region.cellCount.toInt()

    return SurfaceSampler(
      biome = IntLayer(LayerId.BIOME, region, IntArray(cells) { Biome.GRASSLAND.ordinal }),
      soilDepth = FloatLayer(LayerId.SOIL_DEPTH, region, FloatArray(cells) { 1f }),
      waterLevel = FloatLayer(LayerId.WATER_LEVEL, region, FloatArray(cells) { Float.NaN }),
      lakeId = IntLayer(LayerId.LAKE_ID, region, IntArray(cells)),
      temperature = FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { temperature }),
      seed = 0x5EEDL
    )
  }

  /**
   * What share of the ground at this height above the water comes out as strand.
   *
   * An area rather than a line, for [BiomeDitherTest]'s reason: the patch field is spatially coherent, so
   * consecutive samples along a line stay inside one blob and measure its neighbourhood rather than the world's.
   */
  private fun strandShare(
    sampler: SurfaceSampler,
    heightAboveWater: Double,
    steepness: Double,
    across: Int = 140
  ): Double {
    var hits = 0
    for (iy in 0 until across) {
      for (ix in 0 until across) {
        val x = 400.0 + ix * 23.7
        val y = 700.0 + iy * 29.3
        if (sampler.shoreCoverAt(x, y, heightAboveWater, steepness, 15.0) != null) hits++
      }
    }
    return hits.toDouble() / (across * across)
  }

  @Test
  fun `the waterline itself is all strand`() {
    // Nothing is dithered away at zero height: the transition starts here rather than straddling it, so the
    // material below the water and the material at the water's edge are the same thing.
    assertTrue(strandShare(sampler(), heightAboveWater = 0.0, steepness = 0.05) > 0.99)
  }

  @Test
  fun `the strand thins out with height rather than stopping at one`() {
    val sampler = sampler()
    val shares = listOf(0.0, 0.25, 0.5, 0.75).map {
      strandShare(sampler, heightAboveWater = it * 2.0, steepness = 0.05)
    }

    // Strictly falling, and it has to be strict: a rule that jumped from all to nothing would be the same
    // painted line one metre further up the beach.
    for (i in 1 until shares.size) {
      assertTrue(shares[i] < shares[i - 1], "strand share should fall with height, got $shares")
    }

    // And the share of ground is the share the rule asked for, to within a few percent. This is the assertion
    // that earns the rank in `shorePatchAt`: the raw noise is a bell around a half, so before it was ranked this
    // measured 0.99 against an intended 0.75 and the transition was a line at half the strand's height wearing
    // a dither's name.
    val intended = listOf(1.0, 0.75, 0.5, 0.25)
    for (i in shares.indices) {
      assertTrue(
        abs(shares[i] - intended[i]) < 0.06,
        "share at step $i should be about ${intended[i]}, got ${shares[i]}"
      )
    }
  }

  @Test
  fun `a flat coast gets a wider beach than a steep one`() {
    val sampler = sampler()

    // The same height above the water, on ground of two different slopes. Gentle ground carries the strand
    // higher as well as further, because steep ground is shingle and shingle piles less far up.
    val gentle = strandShare(sampler, heightAboveWater = 0.4, steepness = 0.02)
    val steep = strandShare(sampler, heightAboveWater = 0.4, steepness = 0.30)

    assertTrue(gentle > steep, "a gentle shore should hold more strand at the same height, got $gentle vs $steep")

    // Measured horizontally instead: how far inland the strand survives.
    val gentleReach = reachOf(sampler, steepness = 0.02)
    val steepReach = reachOf(sampler, steepness = 0.30)

    assertTrue(
      gentleReach > steepReach * 3.0,
      "a gentle shore should carry a much wider beach, got $gentleReach m against $steepReach m"
    )
  }

  /** How far inland, in metres, the strand still covers any ground at all on this slope. */
  private fun reachOf(sampler: SurfaceSampler, steepness: Double): Double {
    var metres = 0.0
    while (metres < 4_000.0) {
      if (strandShare(sampler, heightAboveWater = metres * steepness, steepness = steepness, across = 40) <= 0.0) {
        return metres
      }
      metres += 1.0
    }
    return metres
  }

  @Test
  fun `a cliff coast is left to the bare rock rule`() {
    val sampler = sampler()

    // No strand at any height on ground this steep. The materialiser answers these columns with the bed that is
    // exposed there, which is what makes a granite headland granite and a limestone one white.
    for (height in listOf(0.0, 0.5, 1.0, 2.0)) {
      assertEquals(
        0.0,
        strandShare(sampler, heightAboveWater = height, steepness = 0.75),
        "a cliff should carry no strand at $height m"
      )
    }
  }

  @Test
  fun `a cold shore is shingle where a temperate one is sand`() {
    val sampler = sampler()

    assertEquals(BlockType.GRAVEL, firstStrand(sampler, temperature = -5.0))
    assertEquals(BlockType.SAND, firstStrand(sampler, temperature = 15.0))
  }

  /** The material of the first column at the waterline that comes out as strand. */
  private fun firstStrand(sampler: SurfaceSampler, temperature: Double): BlockType? {
    for (i in 0 until 400) {
      val cover = sampler.shoreCoverAt(400.0 + i * 23.7, 700.0, 0.0, 0.05, temperature)
      if (cover != null) return cover
    }
    return null
  }

  @Test
  fun `the deep water boundary is mixed rather than drawn`() {
    val sampler = sampler()

    // Either side of the sixty-metre boundary both materials appear, which is the whole difference between a
    // band and a contour line. Sampled as a depth rather than a height, hence the negation.
    val justAbove = materialsAt(sampler, depth = SurfaceCover.DEEP_WATER - 6.0)
    val justBelow = materialsAt(sampler, depth = SurfaceCover.DEEP_WATER + 6.0)

    assertTrue(justAbove.size > 1, "the shallow side of the boundary should hold both materials, got $justAbove")
    assertTrue(justBelow.size > 1, "the deep side of the boundary should hold both materials, got $justBelow")

    // And well clear of it there is nothing left to say, so the ordinary cap answers.
    assertNull(sampler.shoreCoverAt(400.0, 700.0, -(SurfaceCover.DEEP_WATER + 40.0), 0.02, 15.0))
    assertNull(sampler.shoreCoverAt(400.0, 700.0, -10.0, 0.02, 15.0))
  }

  private fun materialsAt(sampler: SurfaceSampler, depth: Double): Set<BlockType> {
    val seen = mutableSetOf<BlockType>()
    for (i in 0 until 400) {
      sampler.shoreCoverAt(400.0 + i * 23.7, 700.0 + i * 29.3, -depth, 0.02, 15.0)?.let { seen += it }
    }
    return seen
  }

  @Test
  fun `the shore is a pure function of world position`() {
    // Two samplers built independently from the same seed, as two chunks either side of a shoreline are. They
    // never consult each other, so agreeing column for column is the only thing that keeps a beach from having
    // a seam down the middle of it.
    val left = sampler()
    val right = sampler()

    var strand = 0
    for (i in 0 until 2_000) {
      val x = 400.0 + i * 7.3
      val y = 700.0 + i * 11.9
      val height = (i % 5) * 0.4

      val a = left.shoreCoverAt(x, y, height, 0.04, 15.0)
      assertEquals(a, right.shoreCoverAt(x, y, height, 0.04, 15.0), "disagreement at $x, $y")
      if (a != null) strand++
    }

    // Count the output before believing the test: an agreement check passes just as well on a rule that answers
    // null everywhere.
    assertTrue(strand > 100, "the sweep should have found real strand, found $strand columns")
  }

  @Test
  fun `ordinary inland ground is not a shore`() {
    val sampler = sampler()
    assertNull(sampler.shoreCoverAt(400.0, 700.0, 120.0, 0.05, 15.0))
    assertNotNull(sampler.shoreCoverAt(400.0, 700.0, 0.0, 0.05, 15.0))
  }
}
