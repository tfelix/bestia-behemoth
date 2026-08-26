package net.bestia.worldgen.hydro

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where a river stops when it reaches the sea.
 *
 * A reach carries the cell it flows into, and at the coast that cell is open water, so a channel left as
 * routing produced it runs a whole cell past the shore. Under the sea it is invisible; on the map it is not,
 * because the map inks the channel over water it has already painted. On this world it measured a median of
 * 600 m and up to 1.3 km of river drawn across the sea before `HydrologyStage` began cutting the mouth back.
 *
 * Against a real world rather than a synthetic reach, because the defect is a disagreement between two ways
 * of asking where the shore is - a per-cell mask and a bicubic contour - and a fixture would have to pick one
 * of them and so could not show the gap at all.
 */
class RiverMouthTest {

  private val world by lazy {
    StandardWorld.build(
      StandardWorld.demoConfig(seed = 11753242L).copy(widthCells = 128, heightCells = 128)
    )
  }

  private val seaLevel by lazy { world.world.config.seaLevel }

  private val surface by lazy { world.world.layers[LayerId.ELEVATION] as FloatLayer }

  private val rivers by lazy {
    world.world.features.all()
      .filter { it.kind == FeatureKind.RIVER_CHANNEL }
      .filterIsInstance<PolylineFeature>()
  }

  private fun depthAt(x: Double, y: Double): Double {
    return seaLevel - surface.sampleBicubic(x, y)
  }

  private fun mouthDepthOf(river: PolylineFeature): Double {
    val mouth = river.centerline.points.last()
    return depthAt(mouth.x, mouth.y)
  }

  @Test
  fun `the world this is pinned to actually has rivers reaching the sea`() {
    // Every assertion below iterates the rivers, so an empty list passes them all vacuously - and a river
    // that stops inland cannot overshoot a coast it never touches.
    assertTrue(rivers.isNotEmpty(), "seed 11753242 at 128 cells used to have 40 rivers and now has none")

    val atTheCoast = rivers.count { abs(mouthDepthOf(it)) <= TOLERANCE }
    assertTrue(
      atTheCoast >= 10,
      "only $atTheCoast of ${rivers.size} rivers end on the shoreline, so this world no longer exercises the cut"
    )
  }

  @Test
  fun `a river stops at the shoreline rather than running on over the sea`() {
    for (river in rivers) {
      assertTrue(
        mouthDepthOf(river) <= TOLERANCE,
        "${river.id} ends under %.0f m of sea".format(mouthDepthOf(river))
      )
    }
  }

  @Test
  fun `a cut mouth reads its bed from where it was cut, not from the end of the reach`() {
    // The station tables stay indexed by position along the *untrimmed* reach, so a cut river takes its bed
    // from the point the shoreline cut it. Index them by the shortened length instead and every mouth
    // inherits the depth of the ocean cell the reach drained into - which on this world reached 360 m.
    val shallow = rivers.count { river ->
      val bed = river.stations.channel(Profiles.CHANNEL_BED_ELEVATION)
      seaLevel - river.stations.valueAt(bed, river.stations.stationCount - 1) < NEAR_SEA_LEVEL_BED
    }

    assertTrue(
      shallow * 2 >= rivers.size,
      "only $shallow of ${rivers.size} rivers reach their mouth with a bed near sea level"
    )
  }

  companion object {

    /**
     * Metres a mouth may sit either side of the shoreline.
     *
     * The cut interpolates linearly between two stations of a bicubic field, so it lands a few centimetres
     * high; the defect it guards against is three orders of magnitude bigger.
     */
    private const val TOLERANCE = 0.5

    /**
     * Metres of bed below sea level that still count as a river arriving at the coast.
     *
     * Not zero. The bed descends towards the ocean cell the reach drained into, so a river crossing a steep
     * shelf is genuinely some way under water by the time it reaches the shoreline - eight to nineteen metres
     * on the worst of them here. What this separates is that from the whole ocean-cell depth.
     */
    private const val NEAR_SEA_LEVEL_BED = 3.0
  }
}
