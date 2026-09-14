package net.bestia.worldgen.voxel

import net.bestia.worldgen.coast.CoastChannels
import net.bestia.worldgen.coast.ShoreKind
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The chunk tier's reader for a traced coastline.
 *
 * On hand-built features for [LavaTest]'s reason: what is under test is how the sampler resolves a column
 * against segments that deliberately overlap, and finding two adjacent segments in a generated world would tie
 * the fixture to the coastline of one seed.
 *
 * Two properties carry the weight. **Exactly one segment answers for a column**, which is what the half-open
 * arc-length claim exists for - nearness alone gives two answers everywhere segments lap, and a chunk that took
 * the nearer one would disagree with the chunk next door about the same column. And **the kind is read and not
 * interpolated**: it is a category living in a channel of `Double`s, so the Catmull-Rom in `sample` would hand
 * back whichever kind sits between a cliff and a marsh.
 */
class CoastShoreTest {

  private var nextId = 0L

  /**
   * One straight segment along y = 0, running from [fromX] to [toX], with a station every 100 m.
   *
   * @param claim the half-open claim on the loop's arc length, in the segment's own coordinates
   */
  private fun segment(
    fromX: Double,
    toX: Double,
    kind: ShoreKind,
    width: Double,
    berm: Double,
    claim: ClosedFloatingPointRange<Double>
  ): MarkerFeature {
    val points = ArrayList<Vec2d>()
    var x = fromX
    while (x <= toX + 1e-9) {
      points.add(Vec2d(x, 0.0))
      x += 100.0
    }

    val stations = StationTable.Builder(points.size)
      .channel(CoastChannels.SHORE_KIND) { kind.ordinal.toDouble() }
      .channel(CoastChannels.BEACH_WIDTH) { width }
      .channel(CoastChannels.BERM_HEIGHT) { berm }
      .channel(CoastChannels.SHORE_SLOPE) { 0.04 }
      .channel(CoastChannels.WAVE_EXPOSURE) { 0.4 }
      .channel(CoastChannels.SEDIMENT_SUPPLY) { 0.6 }
      .channel(CoastChannels.CLAIM_START) { claim.start }
      .channel(CoastChannels.CLAIM_END) { claim.endInclusive }
      .build()

    return MarkerFeature(
      id = FeatureId(nextId++),
      kind = FeatureKind.COASTLINE,
      centerline = Polyline(points),
      stations = stations
    )
  }

  @Test
  fun `a column inside the strand takes the segment's kind and width`() {
    val sampler = CoastShoreSampler(
      listOf(segment(0.0, 1_000.0, ShoreKind.SAND_BEACH, width = 60.0, berm = 2.0, claim = 0.0..1_000.0))
    )

    val shore = sampler.shoreAt(500.0, 30.0)

    assertNotNull(shore)
    assertEquals(ShoreKind.SAND_BEACH, shore.kind)
    assertEquals(60.0, shore.beachWidth, 0.001)
    assertEquals(2.0, shore.bermHeight, 0.001)
    assertEquals(30.0, shore.distance, 0.001)
  }

  @Test
  fun `a column beyond the widest strand is not claimed`() {
    val sampler = CoastShoreSampler(
      listOf(segment(0.0, 1_000.0, ShoreKind.SAND_BEACH, width = 60.0, berm = 2.0, claim = 0.0..1_000.0))
    )

    // Well outside the segment's own reach, so the bounding box test rejects it before any projection.
    assertNull(sampler.shoreAt(500.0, 4_000.0))
  }

  @Test
  fun `exactly one of two overlapping segments answers for a column`() {
    // Two segments lapping over each other between 400 and 600, with claims that meet at 500 and do not
    // overlap. This is the arrangement the stage emits, and the one nearness alone cannot resolve.
    val west = segment(0.0, 600.0, ShoreKind.SAND_BEACH, width = 80.0, berm = 2.0, claim = 0.0..500.0)
    val east = segment(400.0, 1_000.0, ShoreKind.SHINGLE_BEACH, width = 80.0, berm = 3.0, claim = 100.0..600.0)

    val sampler = CoastShoreSampler(listOf(west, east))

    // In the lap, and on the west side of the join: the west segment claims it even though both are equally
    // near. Its own arc length at x = 450 is 450, inside 0..500.
    assertEquals(ShoreKind.SAND_BEACH, sampler.shoreAt(450.0, 10.0)?.kind)

    // Still in the lap, but east of the join: 550 is outside the west claim and 150 is inside the east one.
    assertEquals(ShoreKind.SHINGLE_BEACH, sampler.shoreAt(550.0, 10.0)?.kind)

    // And nowhere in the lap does neither answer, which is the other way the claim could be wrong.
    var x = 400.0
    while (x <= 600.0) {
      assertNotNull(sampler.shoreAt(x, 10.0), "no segment claimed the column at x = $x")
      x += 10.0
    }
  }

  @Test
  fun `the shore kind is read and never interpolated`() {
    // A cliff meeting a marsh. Their ordinals sit either side of a third kind, so a sampler that interpolated
    // this channel would report a rocky shore in the middle - a material that neither neighbour is made of.
    val points = (0..10).map { Vec2d(it * 100.0, 0.0) }
    val kinds = DoubleArray(points.size) {
      if (it < 5) ShoreKind.SEA_CLIFF.ordinal.toDouble() else ShoreKind.SALT_MARSH.ordinal.toDouble()
    }

    val feature = MarkerFeature(
      id = FeatureId(nextId++),
      kind = FeatureKind.COASTLINE,
      centerline = Polyline(points),
      stations = StationTable.Builder(points.size)
        .channel(CoastChannels.SHORE_KIND, kinds)
        .channel(CoastChannels.BEACH_WIDTH) { 90.0 }
        .channel(CoastChannels.BERM_HEIGHT) { 1.0 }
        .channel(CoastChannels.SHORE_SLOPE) { 0.2 }
        .channel(CoastChannels.WAVE_EXPOSURE) { 0.5 }
        .channel(CoastChannels.SEDIMENT_SUPPLY) { 0.2 }
        .channel(CoastChannels.CLAIM_START) { 0.0 }
        // Past the end of the line, which is what the stage gives the last segment of a loop: the claim is
        // half-open, so a claim ending exactly at the line's own length would leave the final column unanswered.
        .channel(CoastChannels.CLAIM_END) { Double.MAX_VALUE }
        .build()
    )

    val sampler = CoastShoreSampler(listOf(feature))

    var x = 0.0
    while (x <= 1_000.0) {
      val kind = sampler.shoreAt(x, 5.0)?.kind
      assertTrue(
        kind == ShoreKind.SEA_CLIFF || kind == ShoreKind.SALT_MARSH,
        "x = $x came back as $kind, which is neither of the two kinds on this segment"
      )
      x += 25.0
    }
  }

  @Test
  fun `a segment missing a channel is skipped rather than fatal`() {
    // A producer bug must not take chunk generation down with it. The invariant harness is what complains.
    val broken = MarkerFeature(
      id = FeatureId(nextId++),
      kind = FeatureKind.COASTLINE,
      centerline = Polyline(listOf(Vec2d(0.0, 0.0), Vec2d(100.0, 0.0))),
      stations = StationTable.Builder(2).channel("something_else") { 1.0 }.build()
    )

    val good = segment(500.0, 1_000.0, ShoreKind.SAND_BEACH, width = 50.0, berm = 2.0, claim = 0.0..500.0)
    val sampler = CoastShoreSampler(listOf(broken, good))

    assertTrue(!sampler.isEmpty)
    assertEquals(ShoreKind.SAND_BEACH, sampler.shoreAt(700.0, 10.0)?.kind)
  }

  @Test
  fun `no coastline at all is the cheap path`() {
    assertTrue(CoastShoreSampler(emptyList()).isEmpty)
    assertNull(CoastShoreSampler(emptyList()).shoreAt(0.0, 0.0))
  }
}
