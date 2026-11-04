package net.bestia.worldgen.voxel

import net.bestia.worldgen.civ.RoadClass
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.LinearFeatures
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoadSurfacesTest {

  /** A road due east along y = 0 from x = 0 to x = 600, with a bridge gap between 280 and 320 if asked. */
  private fun road(roadClass: RoadClass, gap: Boolean = false, id: Long = 7L): PolylineFeature {
    val line = Polyline(listOf(Vec2d(0.0, 0.0), Vec2d(300.0, 0.0), Vec2d(600.0, 0.0))).resample(20.0)
    return LinearFeatures.road(
      id = FeatureId(id),
      centerline = line,
      stationSpacing = 20.0,
      surfaceElevation = { 100.0 },
      halfWidth = { s -> if (gap && s in 260.0..340.0) 0.0 else roadClass.halfWidth },
      shoulder = { s -> if (gap && s in 260.0..340.0) 0.0 else roadClass.shoulder },
      preResampled = true,
      roadClass = roadClass.ordinal
    )
  }

  private val everywhere = Aabb(-100.0, -100.0, 700.0, 100.0)

  @Test
  fun `a column is the same whichever chunk asks about it`() {
    // The seam guarantee: a road is clipped to each chunk's own segments, and two chunks either side of a border
    // must still agree about every column near it.
    for (roadClass in RoadClass.entries) {
      val feature = road(roadClass)
      val left = RoadSurfaces(listOf(feature), Aabb(96.0, -32.0, 128.0, 32.0).expanded(16.0))
      val right = RoadSurfaces(listOf(feature), Aabb(128.0, -32.0, 160.0, 32.0).expanded(16.0))
      val whole = RoadSurfaces(listOf(feature), everywhere)

      var y = -12.0
      while (y <= 12.0) {
        for (x in doubleArrayOf(126.5, 127.5, 128.5, 129.5)) {
          assertEquals(whole.surfaceAt(x, y), left.surfaceAt(x, y), "$roadClass at ($x, $y), left chunk")
          assertEquals(whole.surfaceAt(x, y), right.surfaceAt(x, y), "$roadClass at ($x, $y), right chunk")
        }
        y += 0.5
      }
    }
  }

  @Test
  fun `the tread stays inside the flattened bed`() {
    for (roadClass in RoadClass.entries) {
      val surfaces = RoadSurfaces(listOf(road(roadClass)), everywhere)
      var x = 10.0
      while (x < 590.0) {
        var y = -20.0
        while (y <= 20.0) {
          if (surfaces.surfaceAt(x, y) != null) {
            assertTrue(abs(y) <= roadClass.halfWidth, "$roadClass laid road at ($x, $y), outside its bed")
          }
          y += 0.5
        }
        x += 3.0
      }
    }
  }

  @Test
  fun `there is no tread over a bridge gap`() {
    val surfaces = RoadSurfaces(listOf(road(RoadClass.COBBLED, gap = true)), everywhere)
    var y = -8.0
    while (y <= 8.0) {
      assertNull(surfaces.surfaceAt(300.0, y), "road surface laid in the bridge gap at y = $y")
      y += 0.5
    }
    assertEquals(BlockType.COBBLESTONE, surfaces.surfaceAt(100.0, 0.0), "no cobbles away from the gap")
  }

  @Test
  fun `each class is made of its own material`() {
    // A cobbled road is cobbles down the middle; its wander is a fraction of a metre against a three metre tread.
    val cobbled = RoadSurfaces(listOf(road(RoadClass.COBBLED)), everywhere)
    var x = 20.0
    while (x < 580.0) {
      assertEquals(BlockType.COBBLESTONE, cobbled.surfaceAt(x, 0.0), "no cobbles on the centreline at x = $x")
      x += 7.0
    }

    // The two earth roads: packed earth somewhere, and never a cobble.
    for (roadClass in listOf(RoadClass.TRAIL, RoadClass.DIRT_ROAD)) {
      val surfaces = RoadSurfaces(listOf(road(roadClass)), everywhere)
      val found = HashSet<BlockType?>()
      x = 10.0
      while (x < 590.0) {
        var y = -8.0
        while (y <= 8.0) {
          found.add(surfaces.surfaceAt(x, y))
          y += 0.5
        }
        x += 2.0
      }
      assertTrue(BlockType.PACKED_EARTH in found, "$roadClass laid no packed earth")
      assertTrue(BlockType.COBBLESTONE !in found, "$roadClass laid cobbles")
    }
  }

  @Test
  fun `a trail wanders and changes width along its length`() {
    // What "not a ruled line" means, measured: the tread's middle moves, and its width varies.
    val surfaces = RoadSurfaces(listOf(road(RoadClass.TRAIL)), everywhere)
    val middles = ArrayList<Double>()
    val widths = ArrayList<Double>()

    var x = 10.0
    while (x < 590.0) {
      val covered = (-60..60).map { it * 0.1 }.filter { surfaces.surfaceAt(x, it) != null }
      if (covered.isNotEmpty()) {
        middles.add((covered.first() + covered.last()) * 0.5)
        widths.add(covered.last() - covered.first())
      }
      x += 5.0
    }

    assertTrue(middles.size > 50, "the trail is missing along most of its length")
    assertTrue(middles.max() - middles.min() > 1.5, "the tread runs dead straight: ${middles.min()}..${middles.max()}")
    assertTrue(widths.max() - widths.min() > 1.0, "the tread is one width: ${widths.min()}..${widths.max()}")
  }

  @Test
  fun `a dirt road keeps no puddles where it is told the ground is dry`() {
    val surfaces = RoadSurfaces(listOf(road(RoadClass.DIRT_ROAD)), everywhere)
    var x = 10.0
    while (x < 590.0) {
      var y = -8.0
      while (y <= 8.0) {
        assertTrue(surfaces.surfaceAt(x, y, puddles = false) != BlockType.MUD, "a puddle on a dry road at ($x, $y)")
        y += 0.5
      }
      x += 1.0
    }
  }

  @Test
  fun `a road with no class lays no surface`() {
    // What a street is built as: the same `LinearFeatures.road`, with no class. Streets are for `TownStructures` to pave.
    val street = LinearFeatures.road(
      id = FeatureId(9L),
      centerline = Polyline(listOf(Vec2d(0.0, 0.0), Vec2d(200.0, 0.0))),
      stationSpacing = 20.0,
      surfaceElevation = { 100.0 },
      halfWidth = { 4.0 }
    )
    assertTrue(RoadSurfaces(listOf(street), everywhere).isEmpty)
  }
}
