package net.bestia.worldgen.bio

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.Invariants
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the two volcanic biomes land, and the three things about them that no map would show.
 *
 * - **both of them exist.** A pair placed by an ordered `when` fails asymmetrically: the first rung wins where
 *   they overlap, so an inverted or over-wide `volcanicFieldHeat` makes `GEOTHERMAL_BASIN` a biome that is never
 *   assigned in any world - and a biome that is never assigned looks exactly like one that is merely rare.
 *   `BiomeParams.init` orders the pair and this is what proves the ordering has the effect it is for.
 * - **they are above the ice rung.** A hotspot cone stands at up to 3,800 m, which is far below
 *   `glacierTemperature` by lapse rate, so under the ice rung the summits would all be ice sheet. The assertion
 *   is that a volcanic cell exists *somewhere cold*, which is the case the ordering exists for.
 * - **a volcanic field carries no soil.** `fertilityAt` and `soilDepthAt` both return early for it, and without
 *   those the formula's own floor gives fresh basalt about 0.4 - respectable farmland - and `SettlementStage`
 *   puts a town on the volcano.
 */
class VolcanicBiomeTest {

  private val world: GeneratedWorld by lazy {
    // 192 cells rather than 128: large enough that the two dozen edifices do not overlap into one patch, so the
    // field-against-basin ratio is a measurement rather than an artefact of saturation.
    StandardWorld.build(WorldConfig(seed = 0xC0FFEEL, widthCells = 192, heightCells = 192))
  }

  private fun biomes() = world.world.layers.require<IntLayer>(LayerId.BIOME)
  private fun elevation() = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)

  @Test
  fun `both volcanic biomes are placed, and the field is the smaller of the two`() {
    val counts = HashMap<Biome, Int>()
    val biome = biomes()
    val ground = elevation()
    val seaLevel = world.config.seaLevel

    for (i in biome.data.indices) {
      if (ground.data[i] <= seaLevel) continue
      val kind = Biome.entries[biome.data[i]]
      if (kind == Biome.VOLCANIC_FIELD || kind == Biome.GEOTHERMAL_BASIN) {
        counts[kind] = (counts[kind] ?: 0) + 1
      }
    }

    val field = counts[Biome.VOLCANIC_FIELD] ?: 0
    val basin = counts[Biome.GEOTHERMAL_BASIN] ?: 0
    val share = Invariants.landShareOfBiomes(world, Biome.VOLCANIC_FIELD, Biome.GEOTHERMAL_BASIN)

    println("volcanic cells: field $field, basin $basin, together %.2f%% of the land".format(share * 100))

    assertTrue(field > 0, "no volcanic field on a world with volcanoes on it")
    assertTrue(basin > 0, "no geothermal basin: the ordered pair in BiomeParams has swallowed the second rung")

    // The field is the cone and the basin is the valleys around it, so the basin is the larger. Bounded on both
    // sides: a basin fifteen times the field is the `volcanicFieldHeat` regression, and a basin *smaller* than
    // the field would mean the wetness gate had stopped passing anything.
    assertTrue(
      basin in field..(field * 6),
      "field $field against basin $basin; the pair is meant to be a cone inside its valleys"
    )
  }

  @Test
  fun `every volcanic cell is inside an edifice`() {
    // The structural claim, and the one that cannot be satisfied by accident. Duplicated from the invariant
    // deliberately: the invariant runs in the sweep, and this runs on every `:worldgen:test`.
    val vents = world.world.features.all()
      .filter { it.kind == FeatureKind.VOLCANIC_VENT }
      .filterIsInstance<PointMarker>()
      .map { it.position }

    assertTrue(vents.isNotEmpty(), "the fixture world should have vents")

    val biome = biomes()
    val region = biome.region
    val metres = region.resolution.metresPerCell
    val range = world.params.biome.volcanicVentRange

    for (y in region.minY..region.maxY) {
      for (x in region.minX..region.maxX) {
        val kind = Biome.entries[biome[x, y]]
        if (kind != Biome.VOLCANIC_FIELD && kind != Biome.GEOTHERMAL_BASIN) continue

        val cx = (x + 0.5) * metres
        val cy = (y + 0.5) * metres
        val nearest = vents.minOf { hypot(it.x - cx, it.y - cy) }

        assertTrue(
          nearest <= range + metres * 1.5,
          "($x,$y) is $kind but the nearest vent is ${nearest.toInt()} m away, past ${range.toInt()}"
        )
      }
    }
  }

  @Test
  fun `volcanic ground beats the ice rung on the cells they both claim`() {
    /*
     * The assertion the ladder's ordering exists for, stated against the ice rung's *own* condition rather than
     * against a proxy for it.
     *
     * A cell that is cold enough and wet enough for permanent ice, and comes out volcanic, is a cell the two
     * rungs both matched and the volcanic one won. Count them: if the ordering were the other way round the count
     * would be exactly zero, and a hotspot cone at 3,800 m is far past `glacierTemperature` by lapse rate, so
     * these cells are not a curiosity - they are most of the summits.
     *
     * Written this way after the first version asserted nothing in either branch: it compared the coldest volcanic
     * cell against the threshold, and skipped when the comparison failed, so both paths were vacuous.
     */
    val biome = biomes()
    val temperature = world.world.layers.require<FloatLayer>(LayerId.TEMPERATURE)
    val precipitation = world.world.layers.require<FloatLayer>(LayerId.PRECIPITATION)
    val region = biome.region
    val metres = region.resolution.metresPerCell
    val cold = world.params.biome.glacierTemperature

    var contested = 0
    var volcanic = 0

    for (y in region.minY..region.maxY) {
      for (x in region.minX..region.maxX) {
        val kind = Biome.entries[biome[x, y]]
        if (kind != Biome.VOLCANIC_FIELD && kind != Biome.GEOTHERMAL_BASIN) continue
        volcanic++

        val at = (x + 0.5) * metres to (y + 0.5) * metres
        val wouldBeIce = temperature.sampleBilinear(at.first, at.second) < cold &&
            precipitation.sampleBilinear(at.first, at.second) > BiomeStage.GLACIER_PRECIPITATION
        if (wouldBeIce) contested++
      }
    }

    println("$contested of $volcanic volcanic cells would be ice sheet if the ice rung came first")

    assertTrue(
      contested > 0,
      "no volcanic cell on this world also satisfies the ice rung, so the ordering above it is untested here"
    )
  }

  @Test
  fun `a volcanic field carries no soil and no fertility`() {
    val biome = biomes()
    val soil = world.world.layers.require<FloatLayer>(LayerId.SOIL_DEPTH)
    val fertility = world.world.layers.require<FloatLayer>(LayerId.SOIL_FERTILITY)

    var checked = 0
    for (i in biome.data.indices) {
      if (Biome.entries[biome.data[i]] != Biome.VOLCANIC_FIELD) continue
      checked++
      assertEquals(0f, soil.data[i], "a volcanic field should carry no soil")
      assertEquals(0f, fertility.data[i], "a volcanic field should be barren, or a town settles on it")
    }

    assertTrue(checked > 0, "no volcanic field to check")
  }
}
