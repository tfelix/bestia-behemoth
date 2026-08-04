package net.bestia.worldgen.geo

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The volcanism stage: the field, the craters, and the two properties everything downstream depends on.
 *
 * The assertions worth naming, because they are the ones whose failure is invisible rather than loud:
 *
 * - **the field is a rank.** Every threshold in the biome stage, the resource stage and `LocalTemperature` is
 *   quoted as a percentile of this layer. If it silently became a raw field, a seed with a weak arc would get no
 *   sulfur, no eruptions and no ground heat, and a seed with a strong one would get a tropical tundra - and each
 *   of those looks like a tuning problem rather than like a broken normalisation.
 * - **vent indices are dense from zero.** `HistorySim` keys a deterministic per-vent eruption roll on the index,
 *   so a gap wastes a stream and a duplicate makes two volcanoes erupt in lockstep for the life of the world.
 * - **no vent is under water.** A crater four kilometres down is a thing this deliberately does not model, and
 *   every consumer of a vent is asking about ground a player can reach.
 */
class VolcanismTest {

  private fun config(seed: Long) = WorldConfig(
    seed = seed,
    // Large enough to contain a convergent boundary and a hotspot chain. Below about this the world can
    // legitimately have neither, and then everything here passes while testing nothing.
    widthCells = 160,
    heightCells = 160,
    chunkSize = 32,
    voxelSize = 1.0
  )

  private val world by lazy { StandardWorld.build(config(0xC0FFEEL)) }

  @Test
  fun `the world has craters and they are on dry land`() {
    val vents = ventsOf()
    assertTrue(vents.isNotEmpty(), "a world with convergent boundaries should have vents")

    val bedrock = world.world.layers.require<FloatLayer>(LayerId.BEDROCK_ELEVATION)
    val seaLevel = world.config.seaLevel

    for (vent in vents) {
      val ground = bedrock.sampleBilinear(vent.position.x, vent.position.y)
      assertTrue(
        ground > seaLevel,
        "vent at (${vent.position.x.toInt()}, ${vent.position.y.toInt()}) is ${ground.toInt()} m, " +
            "below the sea level of ${seaLevel.toInt()} m"
      )
    }
  }

  @Test
  fun `vent indices are dense from zero`() {
    val indices = ventsOf()
      .map { it.attribute(VolcanismStage.CHANNEL_INDEX).toInt() }
      .sorted()

    assertEquals(indices.indices.toList(), indices, "vent indices must be dense from zero with no duplicates")
  }

  @Test
  fun `the volcanism field is a percentile rank over the volcanic land`() {
    val field = world.world.layers.require<FloatLayer>(LayerId.VOLCANISM)
    val elevation = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val seaLevel = world.config.seaLevel

    var land = 0
    var volcanic = 0
    var hot = 0
    var maximum = 0.0

    for (i in field.data.indices) {
      val value = field.data[i].toDouble()
      assertTrue(value in 0.0..1.0, "volcanism out of unit range: $value")
      maximum = maxOf(maximum, value)

      if (elevation.data[i] > seaLevel) {
        land++
        if (value > 0.0) volcanic++
        if (value >= HOT) hot++
      }
    }

    assertTrue(land > 0, "the fixture world has no land")
    assertTrue(volcanic > 0, "the fixture world has no volcanic ground to rank")

    // A rank reaches its own top: the hottest volcanic cell in the world is at or very near 1. A raw field of
    // `strength * (1 - d/range)` would top out at whatever the strongest vent happened to be, so this is the
    // assertion that tells a rank from a raw field rather than merely checking the range.
    assertTrue(maximum > 0.99, "a percentile rank should reach 1.0 somewhere, peaked at $maximum")

    /*
     * And the top quarter of the *volcanic* ground is about a quarter of it, which no raw field would be.
     *
     * Over the volcanic cells and not over the land, and that is the whole of the bug this assertion was
     * rewritten for. Ranking all the land put every zero-volcanism cell - about sixty per cent of it - at one
     * rank equal to the height of the spike at zero, so `>= 0.75` came out at 45% of the land on one seed and
     * 99.7% on another. Both were inside the old `0.10..0.40` bound often enough to pass. See
     * `VolcanismStage.rankAgainstLand`.
     */
    val share = hot.toDouble() / volcanic
    assertTrue(
      share in 0.15..0.35,
      "the share of volcanic ground at or above $HOT should be near ${1.0 - HOT}, was $share"
    )

    // The other half of it, and the property every consumer actually depends on: quiet crust reads exactly zero,
    // so `> 0.0` is a valid test for volcanic country and a threshold below the spike cannot select the world.
    val quiet = 1.0 - volcanic.toDouble() / land
    println("volcanism: %.1f%% of the land is quiet crust, %.1f%% of the volcanic ground is above %.2f"
      .format(quiet * 100, share * 100, HOT))
    assertTrue(quiet > 0.20, "only ${"%.1f".format(quiet * 100)}% of the land is unvolcanic; that is not rare")
  }

  @Test
  fun `the volcanism field is zero over water`() {
    // Not a sentinel and not a rank: `LocalTemperature` samples this bilinearly with no NaN handling, so a
    // sentinel would propagate into the air temperature of the whole coastline.
    val field = world.world.layers.require<FloatLayer>(LayerId.VOLCANISM)
    val elevation = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val seaLevel = world.config.seaLevel

    for (i in field.data.indices) {
      if (elevation.data[i] <= seaLevel) {
        assertEquals(0f, field.data[i], "volcanism over water must be zero, was ${field.data[i]}")
      }
    }
  }

  @Test
  fun `a lava pool stores its surface below the summit it sits in`() {
    val pools = world.world.features.all()
      .filter { it.kind == FeatureKind.LAVA_POOL }
      .filterIsInstance<AreaFeature>()

    // Pools are gated on vent strength, so a world may legitimately have none - assert the property rather than
    // the presence, and let the sweep in the invariants speak to whether they ever happen at all.
    val bedrock = world.world.layers.require<FloatLayer>(LayerId.BEDROCK_ELEVATION)

    for (pool in pools) {
      val table = pool.perimeter ?: error("a lava pool must carry its surface elevation")
      val surface = table.valueAt(table.channel(VolcanismStage.CHANNEL_SURFACE_ELEVATION), 0)
      val centre = pool.ring.bbox.let { (it.minX + it.maxX) * 0.5 to (it.minY + it.maxY) * 0.5 }
      val summit = bedrock.sampleBilinear(centre.first, centre.second)

      assertTrue(
        surface < summit,
        "a crater lake at ${surface.toInt()} m must sit below the ${summit.toInt()} m cone around it"
      )
      assertTrue(table.periodic, "an area feature's perimeter table has to wrap")
    }
  }

  @Test
  fun `hotspot cones are recorded and only the young ones get craters`() {
    val cones = world.world.features.all()
      .filter { it.kind == FeatureKind.HOTSPOT }
      .filterIsInstance<PointMarker>()

    assertTrue(cones.isNotEmpty(), "the hotspot pass stamps cones, so it should record them")

    // Every chain index the tectonics stage draws is inside the chain length it was told to draw.
    for (cone in cones) {
      val index = cone.attribute(TectonicsStage.CHANNEL_CHAIN_INDEX).toInt()
      assertTrue(index >= 0, "a chain index is an offset along a track, not a sentinel")
    }

    // The interesting half: the vents derived from them are a strict minority, because a chain is mostly
    // extinct. This is the assertion that catches `activeChainLength` being ignored.
    val hotspotVents = ventsOf()
      .filter { it.attribute(VolcanismStage.CHANNEL_ORIGIN).toInt() == VentOrigin.HOTSPOT.ordinal }

    assertTrue(
      hotspotVents.size < cones.size,
      "a hotspot chain is mostly extinct cones: ${hotspotVents.size} vents from ${cones.size} cones"
    )
  }

  private fun ventsOf(): List<PointMarker> = world.world.features.all()
    .filter { it.kind == FeatureKind.VOLCANIC_VENT }
    .filterIsInstance<PointMarker>()

  private companion object {
    /** `WeatherParams.geothermalFloor`, so the share assertion measures the threshold something really uses. */
    const val HOT = 0.75
  }
}
