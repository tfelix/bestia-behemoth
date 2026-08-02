package net.bestia.worldgen.vector

import net.bestia.worldgen.geo.GlacialStage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The areal feature's contract, and the two guards that stop it being used where it cannot work.
 */
class AreaFeatureTest {

  private fun pond(
    centre: Vec2d = Vec2d(12_000.0, 8_000.0),
    radius: Double = 180.0,
    depth: Double = 6.0,
    floor: Double = 200.0
  ): AreaFeature {
    val ring = Ring.warpedCircle(centre, radius, seed = 31L, vertexCount = 20)
    val stations = StationTable.Builder(ring.vertexCount, periodic = true)
      .channel(AreaProfiles.CHANNEL_FLOOR_ELEVATION) { floor }
      .channel(AreaProfiles.CHANNEL_DEPTH) { depth }
      .channel(AreaProfiles.CHANNEL_SHORE_REACH) { 40.0 }
      .channel(AreaProfiles.CHANNEL_FLOOR_EXPONENT) { 1.6 }
      .build()

    return AreaFeature(
      id = FeatureId(1L),
      kind = FeatureKind.LAKE,
      ring = ring,
      profile = AreaProfiles.bowl(stations),
      perimeter = stations,
      skirt = 10.0
    )
  }

  /** Collects whatever a feature emits at one column. */
  private class Collector : HeightModSink {
    var count = 0
    var height = Double.NaN
    var weight = Double.NaN
    override fun add(featureId: FeatureId, priority: Int, blend: BlendMode, value: Double, weight: Double) {
      count++
      this.height = value
      this.weight = weight
    }
  }

  private fun evaluate(feature: AreaFeature, x: Double, y: Double, base: Double = 210.0): Collector {
    val sink = Collector()
    feature.evaluateColumn(x, y, base, DoubleArray(feature.scratchSize), sink)
    return sink
  }

  @Test
  fun `an areal feature can never reach the coarse raster carve`() {
    // Not an observation about today's stage order - an assertion about the predicate the carve filters on.
    // `GlacialStage.carveInto` walks outline() and stamps a band corridorWidthMax wide along it, which on a
    // ring paints the shore and misses the water. The check exists so a future producer emitting a MIN-blend
    // area from a glacial-visible stage fails loudly instead of generating a lake with a hole in it.
    assertFalse(GlacialStage.isRasterisable(pond()), "an area is not rasterisable by an outline walk")

    val corridor = PolylineFeature(
      id = FeatureId(2L),
      kind = FeatureKind.RIVER_CHANNEL,
      centerline = Polyline(listOf(Vec2d(0.0, 0.0), Vec2d(100.0, 0.0))),
      stations = StationTable.Builder(2)
        .channel(PolylineFeature.CORRIDOR_CHANNEL, doubleArrayOf(10.0, 10.0))
        .build(),
      profile = { _, _, _, base -> base - 1.0 }
    )
    assertTrue(GlacialStage.isRasterisable(corridor), "a corridor still is, or the check is too broad")
  }

  @Test
  fun `influence reaches the skirt and no further, which is what the index is told`() {
    val feature = pond(radius = 180.0)

    assertEquals(10.0, feature.corridorWidthMax, 1e-12, "the skirt, not the ring's radius")
    // The bbox must cover the ring plus exactly the skirt, or the index will hand out columns the feature
    // silently declines to answer for - or worse, miss columns it would have answered.
    assertEquals(feature.ring.bbox.minX - 10.0, feature.bbox.minX, 1e-9)
    assertEquals(feature.ring.bbox.maxY + 10.0, feature.bbox.maxY, 1e-9)

    // Nothing is emitted beyond the skirt, walking outwards from the centroid in every direction.
    for (step in 0 until 64) {
      val theta = step * 2.0 * Math.PI / 64
      val far = feature.ring.project(
        Vec2d(
          feature.ring.centroid.x + Math.cos(theta) * 10_000.0,
          feature.ring.centroid.y + Math.sin(theta) * 10_000.0
        )
      ).point
      val outward = Vec2d(Math.cos(theta), Math.sin(theta))
      val justOutside = Vec2d(far.x + outward.x * 10.5, far.y + outward.y * 10.5)
      assertEquals(0, evaluate(feature, justOutside.x, justOutside.y).count, "beyond the skirt at $theta")
    }
  }

  @Test
  fun `the interior is at full weight and the shore eases in`() {
    val feature = pond()
    val inside = evaluate(feature, feature.ring.centroid.x, feature.ring.centroid.y)

    assertEquals(1, inside.count, "the interior is evaluated")
    assertEquals(1.0, inside.weight, 1e-12, "and at full weight - a lake bed is not half applied")

    // A column a little way outside is partly weighted, and monotonically less so further out.
    val boundary = feature.ring.project(
      Vec2d(feature.ring.centroid.x + 10_000.0, feature.ring.centroid.y)
    ).point

    var previous = 1.0
    for (out in listOf(1.0, 3.0, 6.0, 9.0)) {
      val sink = evaluate(feature, boundary.x + out, boundary.y)
      assertEquals(1, sink.count, "still inside the skirt at $out m")
      assertTrue(sink.weight < previous, "weight did not fall between the last step and $out m")
      previous = sink.weight
    }
  }

  @Test
  fun `depth is measured inward from the shore, not outward from a centre`() {
    // The property that makes this profile different from RadialProfiles.bowl, and the reason a crescent
    // pond is possible at all: a long thin lake must be its full depth along its whole length.
    val ring = Ring.crescent(
      centre = Vec2d(30_000.0, 30_000.0),
      radius = 400.0,
      bearing = Vec2d(1.0, 0.0),
      bite = 0.5,
      seed = 9L
    )
    val stations = StationTable.Builder(ring.vertexCount, periodic = true)
      .channel(AreaProfiles.CHANNEL_FLOOR_ELEVATION) { 100.0 }
      .channel(AreaProfiles.CHANNEL_DEPTH) { 8.0 }
      .channel(AreaProfiles.CHANNEL_SHORE_REACH) { 30.0 }
      .channel(AreaProfiles.CHANNEL_FLOOR_EXPONENT) { 1.6 }
      .build()
    val lake = AreaFeature(
      id = FeatureId(3L),
      kind = FeatureKind.OXBOW_LAKE,
      ring = ring,
      profile = AreaProfiles.bowl(stations),
      perimeter = stations
    )

    // Every point at least `shore_reach` inside must be at the full floor elevation, wherever it is in the
    // crescent - including out near the horns, which a radial bowl would leave almost undisturbed.
    var deepColumns = 0
    var y = ring.bbox.minY
    while (y <= ring.bbox.maxY) {
      var x = ring.bbox.minX
      while (x <= ring.bbox.maxX) {
        if (ring.contains(x, y) && ring.project(Vec2d(x, y)).distance > 31.0) {
          val sink = evaluate(lake, x, y, base = 140.0)
          assertEquals(100.0, sink.height, 1e-9, "deep water at ($x, $y) is not at the floor")
          deepColumns++
        }
        x += 5.0
      }
      y += 5.0
    }
    assertTrue(deepColumns > 200, "only $deepColumns deep columns were testable")
  }

  @Test
  fun `an area larger than the index can carry is refused`() {
    val huge = Ring(
      listOf(
        Vec2d(0.0, 0.0),
        Vec2d(AreaFeature.MAX_AREA_EXTENT + 10.0, 0.0),
        Vec2d(AreaFeature.MAX_AREA_EXTENT + 10.0, 500.0),
        Vec2d(0.0, 500.0)
      )
    )
    val failure = assertFailsWith<IllegalArgumentException> {
      AreaFeature(FeatureId(4L), FeatureKind.LAKE, huge)
    }
    // The message has to name the escape hatch, because the person hitting this is the person who needs it.
    assertTrue(failure.message!!.contains("tiled"), "message was: ${failure.message}")
  }

  @Test
  fun `a perimeter table must be periodic and must match the ring vertex for vertex`() {
    val ring = Ring.warpedCircle(Vec2d(0.0, 0.0), 100.0, seed = 1L, vertexCount = 12)

    assertFailsWith<IllegalArgumentException>("an open table") {
      AreaFeature(
        FeatureId(5L), FeatureKind.LAKE, ring,
        perimeter = StationTable.Builder(12).channel("depth") { 1.0 }.build()
      )
    }
    assertFailsWith<IllegalArgumentException>("the wrong station count") {
      AreaFeature(
        FeatureId(6L), FeatureKind.LAKE, ring,
        perimeter = StationTable.Builder(11, periodic = true).channel("depth") { 1.0 }.build()
      )
    }
  }

  @Test
  fun `a null profile makes it geometry and attributes only`() {
    val marker = AreaFeature(FeatureId(7L), FeatureKind.LAKE, Ring.warpedCircle(Vec2d.ZERO, 100.0, 2L))

    assertFalse(marker.affectsHeight, "chunk generation must skip it entirely")
    assertEquals(0, evaluate(marker, 0.0, 0.0).count, "and it must emit nothing if asked anyway")
    assertEquals(1, marker.outline().size, "but it still draws")
  }

  @Test
  fun `the shore is continuous where the skirt meets the interior`() {
    // The failure this catches is a step at the boundary: inside is full weight by fiat and outside is a
    // smoothstep, so if the two do not meet at 1.0 there is a rim exactly on the shoreline.
    val feature = pond()
    val boundary = feature.ring.project(
      Vec2d(feature.ring.centroid.x, feature.ring.centroid.y + 10_000.0)
    ).point

    val outward = evaluate(feature, boundary.x, boundary.y + 0.001)
    val inward = evaluate(feature, boundary.x, boundary.y - 0.001)

    assertEquals(1, outward.count)
    assertEquals(1, inward.count)
    assertTrue(abs(outward.weight - inward.weight) < 1e-3, "weight stepped at the shore")
    assertTrue(abs(outward.height - inward.height) < 1e-2, "height stepped at the shore")
  }
}
