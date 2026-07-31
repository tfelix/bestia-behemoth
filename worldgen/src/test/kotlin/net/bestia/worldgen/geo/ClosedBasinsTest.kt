package net.bestia.worldgen.geo

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.Invariants
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Polyline
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tectonic closed basins: the second lake source, and the one an ice-free world depends on.
 *
 * Two halves. The synthetic tests pin the geometric guarantee - that a carved basin *is* a closed depression,
 * by arithmetic rather than by tuning - because that is what lets the lake-existence invariant be stated at all.
 * The pipeline tests then check that the guarantee survives contact with the rest of the world, which is where
 * the equivalent claim about glacial troughs turned out to be interesting.
 */
class ClosedBasinsTest {

  // --- The geometric guarantee, on terrain this file controls -----------------------------------------

  private val config = WorldConfig(seed = 0xBA51D5L, widthCells = 128, heightCells = 128)
  private val region = CellRegion.world(128, 128, Resolution.KILOMETRE)
  private val metres = Resolution.KILOMETRE.metresPerCell

  /**
   * Rolling upland: high enough everywhere that the freeboard clamp never binds, rough enough that the sill
   * ring is not a constant.
   *
   * Both matter. Flat ground makes the ring arithmetic trivially true, and ground near sea level tests the
   * clamp instead of the geometry.
   */
  private fun upland(relief: Double = 300.0) = Grid(region.width, region.height) { x, y ->
    600.0 + relief * Noise.fbm(1234L, x / 24.0, y / 24.0, 4)
  }

  private fun uniform(value: Double) = Grid(region.width, region.height) { _, _ -> value }

  private fun place(
    elevation: Grid,
    uplift: Grid = uniform(1.0),
    crustAge: Grid = uniform(0.7),
    oceanDistance: Grid = uniform(60_000.0),
    rifts: List<Polyline> = emptyList(),
    params: ClosedBasinParams = ClosedBasinParams()
  ) = ClosedBasins.place(
    config = config,
    rng = GenRng.derive(config.seed, ErosionStage.ID, 4, 1L),
    region = region,
    elevation = elevation,
    uplift = uplift,
    crustAge = crustAge,
    oceanDistance = oceanDistance,
    rifts = rifts,
    params = params
  )

  @Test
  fun `the carve leaves the whole sill ring above the floor it digs`() {
    // The whole argument for this pass, restated as a test. If it holds, a basin is a closed depression that
    // Priority-Flood must fill, and every claim about lakes downstream follows. If it does not, the basin is a
    // dent that drains and nothing says so - which is exactly how the pipeline spent a year with no lakes.
    //
    // The first version of this test asserted the ring was left *unchanged*, which is stronger than the design
    // guarantees and stronger than the depression needs: a ring cell standing well above the sill can be shaved
    // by the profile without opening a way out. What has to hold - and does, by the `depth / q` term in the rim
    // height - is that nothing in the band ends up below the sill.
    val params = ClosedBasinParams()
    val before = upland()
    val after = Grid(region.width, region.height) { x, y -> before[x, y] }

    val basins = place(before, params = params)
    assertTrue(basins.size >= 3, "only ${basins.size} basins to check the guarantee against")

    ClosedBasins.carve(after, basins, region, params)

    for (basin in basins) {
      val ringInner = basin.radius - metres * params.ringCells
      var ringCells = 0
      var deepest = Double.MAX_VALUE

      forEachCell(basin) { x, y, distance ->
        deepest = minOf(deepest, after[x, y])
        if (distance < ringInner) return@forEachCell

        ringCells++
        assertTrue(
          after[x, y] >= basin.sill - TOLERANCE,
          "basin at ${basin.centre} cut its sill ring at ($x,$y) down to ${after[x, y]}, " +
              "below its own sill of ${basin.sill}"
        )
      }

      assertTrue(ringCells >= 8, "basin at ${basin.centre} has only $ringCells ring cells to be bounded by")

      // And the other half: something inside is below the sill by very nearly the whole subsidence. Not exactly,
      // because the subsidence is defined at the basin's centre and the nearest cell centre to it can be half a
      // diagonal away and so a little way up the profile - which is the whole reason `exponent` is four rather
      // than two. A tenth of slack catches a profile change that flattens the floor without pinning the
      // arithmetic; against a paraboloid the same worlds came out 13% short.
      assertTrue(
        deepest <= basin.sill - basin.depth * 0.9,
        "basin at ${basin.centre} bottoms out at ${deepest.toInt()} m, only " +
            "${(basin.sill - deepest).toInt()} m under its ${basin.sill.toInt()} m sill of a wanted " +
            "${basin.depth.toInt()} m"
      )
    }
  }

  @Test
  fun `a basin's disc never runs off the edge of the world`() {
    // The other way the guarantee can be void, and the way the test above found it: a disc the grid edge clips
    // has an arc rather than a ring, and the depression inside it can drain out through the side that was cut
    // off. Cheap to assert and cheap to enforce, because a candidate that close to the edge is inside the forced
    // ocean margin anyway.
    val bounds = region.toWorld()

    for (basin in place(upland())) {
      assertTrue(
        basin.centre.x - basin.radius >= bounds.minX && basin.centre.x + basin.radius <= bounds.maxX &&
            basin.centre.y - basin.radius >= bounds.minY && basin.centre.y + basin.radius <= bounds.maxY,
        "a basin of radius ${basin.radius.toInt()} m at ${basin.centre} hangs over the edge of the world"
      )
    }
  }

  @Test
  fun `a basin keeps its floor above sea level even on low ground`() {
    // The reason `freeboard` exists. A closed basin below the waterline is not an inland sea: `FlowRouting`
    // calls everything under sea level ocean and `Lakes` skips ocean cells, so what it makes is a pocket of sea
    // in the middle of a continent with no coast to it and no lake in it.
    val params = ClosedBasinParams()
    val lowland = Grid(region.width, region.height) { x, y ->
      40.0 + 18.0 * Noise.fbm(99L, x / 20.0, y / 20.0, 3)
    }

    val basins = place(lowland, params = params)
    assertTrue(basins.isNotEmpty(), "no basin was placed on ground that is entirely dry land")

    for (basin in basins) {
      assertTrue(
        basin.floor > config.seaLevel,
        "a basin on ground 22 to 58 m high put its floor at ${basin.floor.toInt()} m"
      )
      assertTrue(
        basin.depth >= params.minUsefulDepth - TOLERANCE,
        "basin at ${basin.centre} was clamped to ${basin.depth} m, below the useful minimum"
      )
    }
  }

  @Test
  fun `nothing is placed on ground that is under water`() {
    // A world with no land is a legitimate input - a stage cannot assume its predecessor found a continent -
    // and the answer has to be "no basins" rather than one carved into the sea floor.
    assertTrue(place(uniform(-2_000.0)).isEmpty(), "a basin was carved into the ocean floor")
  }

  @Test
  fun `a basin goes to the quiet half of a world rather than the active one`() {
    // Uplift is the strongest term in the score, and the one whose threshold was measured rather than reasoned
    // into place - see `ClosedBasinParams.quietUplift`. This is what pins it: with everything else held
    // constant, no basin should end up in ground rising at an orogen's rate while quiet crust is available.
    val divide = config.widthMetres * 0.5
    val uplift = Grid(region.width, region.height) { x, _ ->
      if ((x + 0.5) * metres < divide) 0.8 else 9.0
    }

    val basins = place(upland(), uplift = uplift)
    assertTrue(basins.isNotEmpty(), "no basins to compare the two halves with")

    for (basin in basins) {
      assertTrue(
        basin.centre.x < divide,
        "a basin was placed at x=${basin.centre.x.toInt()} m, in the half rising at 9 m per timestep"
      )
    }
  }

  private inline fun forEachCell(basin: ClosedBasin, action: (Int, Int, Double) -> Unit) {
    val cells = (basin.radius / metres).toInt() + 2
    val centreX = (basin.centre.x / metres).toInt()
    val centreY = (basin.centre.y / metres).toInt()

    for (y in (centreY - cells)..(centreY + cells)) {
      for (x in (centreX - cells)..(centreX + cells)) {
        if (x !in 0 until region.width || y !in 0 until region.height) continue
        val distance = hypot((x + 0.5) * metres - basin.centre.x, (y + 0.5) * metres - basin.centre.y)
        if (distance > basin.radius) continue
        action(x, y, distance)
      }
    }
  }

  // --- And on real worlds ----------------------------------------------------------------------------

  private val reference: GeneratedWorld by lazy {
    StandardWorld.build(StandardWorld.demoConfig(seed = StandardWorld.DEFAULT_SEED))
  }

  @Test
  fun `a basin holds water unless something later cut through its rim`() {
    // The guarantee holds at the moment the basin is carved. `GlacialStage` then cuts troughs into the same
    // surface, and a trough crossing a basin's ring legitimately drains it - which is a landform, not a bug,
    // and is why this is stated as a disjunction rather than as "every basin holds water".
    //
    // Written this way rather than loosened to "most of them" because the disjunction is the property actually
    // believed: a basin either holds water or its rim is demonstrably breached, and a failure of both would
    // mean the carve never reached the raster at all.
    val elevation = reference.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val lakes = reference.world.layers.require<IntLayer>(LayerId.LAKE_ID)
    val params = ClosedBasinParams()
    val cell = elevation.region.resolution.metresPerCell

    val basins = reference.world.features.all()
      .filter { it.kind == FeatureKind.TECTONIC_BASIN }
      .filterIsInstance<PointMarker>()

    assertTrue(basins.isNotEmpty(), "the reference world carved no closed basin at all")

    var wet = 0
    for (marker in basins) {
      val radius = marker.attribute(ClosedBasins.CHANNEL_RADIUS)
      val depth = marker.attribute(ClosedBasins.CHANNEL_DEPTH)
      val sill = marker.attribute(ClosedBasins.CHANNEL_FLOOR) + depth
      val ringInner = radius - cell * params.ringCells

      var holdsWater = false
      var breached = false
      val span = (radius / cell).toInt() + 2
      val centreX = (marker.position.x / cell).toInt()
      val centreY = (marker.position.y / cell).toInt()

      for (y in (centreY - span)..(centreY + span)) {
        for (x in (centreX - span)..(centreX + span)) {
          if (x !in elevation.region.minX..elevation.region.maxX) continue
          if (y !in elevation.region.minY..elevation.region.maxY) continue
          val distance = hypot((x + 0.5) * cell - marker.position.x, (y + 0.5) * cell - marker.position.y)
          if (distance > radius) continue

          if (lakes[x, y] != 0) holdsWater = true
          if (distance >= ringInner && elevation[x, y] < sill - TOLERANCE) breached = true
        }
      }

      if (holdsWater) wet++
      assertTrue(
        holdsWater || breached,
        "basin ${marker.id} at ${marker.position} is ${depth.toInt()} m deep, holds no water, " +
            "and its sill ring is intact at ${sill.toInt()} m"
      )
    }

    assertTrue(wet > 0, "not one of ${basins.size} closed basins on the reference world holds any water")
  }

  @Test
  fun `a small world has lakes, which is the whole point of this pass`() {
    // The motivating measurement. Glacial overdeepening gave the 512 km reference world a hundred and fifteen
    // lakes and gave the 128 km world - the one `zone-server` actually boots - none at all: it has thirty-six
    // glacial features, all of them troughs that run to the sea. Asserted at the size that was dry rather than
    // at the size that already worked, because the reference world would have passed this against the bug.
    val small = StandardWorld.build(
      StandardWorld.demoConfig(seed = StandardWorld.DEFAULT_SEED).copy(widthCells = 128, heightCells = 128)
    )

    val basins = small.world.features.all().count { it.kind == FeatureKind.TECTONIC_BASIN }
    val lakes = Invariants.lakeCount(small)

    assertTrue(basins > 0, "a 128 km world was given no closed basin")
    assertTrue(lakes > 0, "a 128 km world with $basins closed basins in it holds no standing water")
  }

  private companion object {
    /** Metres of float slack. Elevations are held as floats, so an exact comparison is not available. */
    const val TOLERANCE = 1e-3
  }
}
