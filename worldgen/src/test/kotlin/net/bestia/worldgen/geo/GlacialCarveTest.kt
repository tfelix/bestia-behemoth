package net.bestia.worldgen.geo

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The glacial stage reaching the raster, and the size it is allowed to be while doing it.
 *
 * These are regression tests for one defect with two faces. Nothing downstream declared `GlacialStage`, so its
 * troughs existed only at chunk-materialisation time - and because a chunk is 32 m across, a trough of
 * impossible width looked from there like ordinary flat ground. So the stage was both invisible to every
 * decision the pipeline makes *and* wrong about how wide a valley is, and neither fault could reveal the other.
 */
class GlacialCarveTest {

  private val generated: GeneratedWorld by lazy {
    StandardWorld.build(StandardWorld.demoConfig(seed = StandardWorld.DEFAULT_SEED))
  }

  private val troughs: List<PolylineFeature> by lazy {
    generated.world.features.all()
      .filter { it.kind == FeatureKind.GLACIAL_TROUGH || it.kind == FeatureKind.FJORD }
      .filterIsInstance<PolylineFeature>()
  }

  @Test
  fun `a trough is a valley rather than a dent in a continent`() {
    assertTrue(troughs.isNotEmpty(), "the reference world has no troughs to measure")

    // The stage's own opening paragraph puts real troughs at one to three kilometres wide in total. Before the
    // cap the corridor half-widths on this world ran to a median of 8.7 km and a maximum of 93 km, so this
    // fails hard against the old behaviour rather than by a margin.
    val widest = troughs.maxOf { it.corridorWidthMax }

    assertTrue(
      widest <= WIDEST_PLAUSIBLE_HALF_WIDTH,
      "the widest trough corridor is ${widest.toInt()} m of half-width, which is a landform no ice cut"
    )
  }

  @Test
  fun `the carve reaches the raster the rest of the pipeline reads`() {
    val eroded = generated.world.layers.require<FloatLayer>(LayerId.ERODED_ELEVATION)
    val elevation = generated.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val region = elevation.region

    // ELEVATION is the fluvial surface with ice cut into it, so it may sit below ERODED_ELEVATION and must
    // never sit above it: every glacial feature that touches the raster is a MIN blend.
    var lowered = 0
    var raised = 0
    for (y in region.minY..region.maxY) {
      for (x in region.minX..region.maxX) {
        val difference = elevation[x, y] - eroded[x, y]
        if (difference < -CARVE_TOLERANCE) lowered++
        if (difference > CARVE_TOLERANCE) raised++
      }
    }

    assertTrue(lowered > 0, "no cell was carved, so the trough features never reached the raster")
    assertTrue(raised == 0, "$raised cells were raised by a stage that only carves")
  }

  @Test
  fun `the coarse carve agrees with the profile the chunks stamp`() {
    // The property that makes carving twice safe. A trough imposes an absolute floor under a MIN blend, so
    // stamping it again over an already-carved base has to be a no-op - which is what lets the raster tier
    // decide *where* the valley is while the vector tier keeps deciding what it looks like.
    val elevation = generated.world.layers.require<FloatLayer>(LayerId.ELEVATION)

    var checked = 0
    for (trough in troughs.sortedBy { it.id.value }.take(12)) {
      val floors = trough.stations.channel(Profiles.CHANNEL_FLOOR_ELEVATION)

      // Midpoint of the reach, which is where the corridor is widest and the floor least affected by the
      // end taper.
      val midpoint = trough.centerline.pointAt(trough.centerline.length * 0.5)
      val floor = trough.stations.sample(floors, 0.5)
      val ground = elevation.sampleBilinear(midpoint.x, midpoint.y)

      // The raster is a kilometre grid sampling a corridor a couple of kilometres wide, so it cannot reproduce
      // the floor exactly - but it must be down there with it rather than up on the untouched surface.
      assertTrue(
        ground <= floor + COARSE_FLOOR_SLACK,
        "trough ${trough.id} has a floor at ${floor.toInt()} m and coarse ground at ${ground.toInt()} m"
      )
      checked++
    }

    assertTrue(checked > 0, "no trough was checked")
  }

  @Test
  fun `a glaciated world has lakes in its troughs`() {
    // The consequence worth having, and the reason this phase is one change rather than two. An overdeepened
    // trough floor is a closed basin by construction - the floor is a running minimum with the overdeepening
    // subtracted on top - so once the carve reaches the raster, priority-flood finds basins and `Lakes` fills
    // them. Before it, LAKE_ID was zero on every world at every size.
    val lakes = generated.world.layers.require<IntLayer>(LayerId.LAKE_ID)
    val region = lakes.region

    val ids = HashSet<Int>()
    for (y in region.minY..region.maxY) {
      for (x in region.minX..region.maxX) {
        val id = lakes[x, y]
        if (id != 0) ids.add(id)
      }
    }

    assertTrue(
      ids.isNotEmpty(),
      "a world with ${troughs.size} troughs in it has no lake anywhere"
    )
  }

  @Test
  fun `a trough floor still descends overall`() {
    // The overdeepening breaks monotonicity locally - that is what makes the basins - so this asserts the
    // weaker property that actually has to hold: a trough runs downhill from head to snout on the whole.
    for (trough in troughs.sortedBy { it.id.value }.take(24)) {
      val floors = trough.stations.channel(Profiles.CHANNEL_FLOOR_ELEVATION)
      val head = trough.stations.sample(floors, 0.0)
      val snout = trough.stations.sample(floors, 1.0)

      assertTrue(
        snout <= head + abs(head) * 1e-9,
        "trough ${trough.id} rises from ${head.toInt()} m at its head to ${snout.toInt()} m at its snout"
      )
    }
  }

  private companion object {
    /**
     * Metres of corridor half-width beyond which a trough has stopped being one.
     *
     * `maxFloorHalfWidth` of 900 m times `wallSpread` times the corridor's overshoot headroom lands a shade
     * under 3 km, so this leaves room for the constants to move without leaving room for the old behaviour.
     */
    const val WIDEST_PLAUSIBLE_HALF_WIDTH = 4_000.0

    /** Metres of float slack when deciding whether a cell moved at all. */
    const val CARVE_TOLERANCE = 1e-6

    /**
     * Metres the coarse carve may sit above a trough's own floor.
     *
     * Generous on purpose: one cell of a kilometre grid straddles a corridor a couple of kilometres wide, and
     * bilinear sampling of it mixes in the untouched neighbours. What is being asserted is that the raster
     * knows there is a valley here, not that it reproduces the cross-section - that is the vector tier's job.
     */
    const val COARSE_FLOOR_SLACK = 220.0
  }
}
