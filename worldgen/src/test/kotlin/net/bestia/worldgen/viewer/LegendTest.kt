package net.bestia.worldgen.viewer

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three things that made the map unreadable: unnamed categories, unlabelled feature colours, and
 * settlements drawn as a single pixel.
 */
class LegendTest {

  private val config = WorldConfig(seed = 1L, widthCells = 8, heightCells = 8)
  private val region = CellRegion.world(8, 8, Resolution.KILOMETRE)

  // ---- names for ids -----------------------------------------------------------------------------

  @Test
  fun `a biome raster reads as biome names, not ordinals`() {
    val layer = IntLayer(LayerId.BIOME, region, IntArray(64) { Biome.TEMPERATE_FOREST.ordinal })
    val field = layer.asField()

    assertEquals("temperate forest", field.format(field.valueAt(2500.0, 2500.0)))
  }

  @Test
  fun `an unknown biome ordinal falls back to its number rather than to the last biome`() {
    // A clamping reader turns an out-of-range id into a confident `cliff` - which is worse than a
    // number, because it is a plausible answer that is wrong.
    val labels = Labels.forLayer(LayerId.BIOME)!!

    assertEquals("ocean", labels(Biome.OCEAN.ordinal))
    assertNull(labels(Biome.entries.size + 5))
  }

  @Test
  fun `a lake id says whether the basin drains to the sea`() {
    val labels = Labels.forLayer(LayerId.LAKE_ID)!!

    assertEquals("none", labels(0))
    assertEquals("4", labels(4))
    assertEquals("4 (endorheic)", labels(-4), "a negative basin is a salt lake and must not read as a number")
  }

  @Test
  fun `a flow direction reads as a compass bearing`() {
    val labels = Labels.forLayer(LayerId.FLOW_DIRECTION)!!

    assertEquals("E", labels(0))
    assertEquals("outflow", labels(D8.NONE))
    assertEquals(D8.DX.size, D8.NAMES.size, "a name per direction, or the names are off by one")
  }

  @Test
  fun `a layer whose ids are only ids keeps showing numbers`() {
    // Plate ids have no vocabulary - a plate is only ever "the same one as over there".
    assertNull(Labels.forLayer(LayerId.PLATE_ID))

    val layer = IntLayer(LayerId.PLATE_ID, region, IntArray(64) { 7 })
    assertEquals("7", layer.asField().format(7.0))
  }

  // ---- the categorical legend -------------------------------------------------------------------

  @Test
  fun `a categorical field reports what is on screen instead of a value range`() {
    // Two thirds forest, one third desert. The old colour bar stretched a gradient over the 1st..99th
    // percentile of the *ordinals* and labelled it "8 .. 13", which is not a legend.
    val layer = IntLayer(LayerId.BIOME, region, IntArray(64) {
      if (it % 3 == 0) Biome.DESERT.ordinal else Biome.TEMPERATE_FOREST.ordinal
    })
    val field = layer.asField()

    val map = MapRenderer(config).render(field, Viewport.fit(config.worldBounds, 60, 60))

    assertTrue(field.palette.categorical, "a biome palette is a set of labels, not a scale")
    assertEquals(2, map.categories.size)
    assertEquals(
      Biome.TEMPERATE_FOREST.ordinal.toDouble(), map.categories.first().first,
      "commonest first, so the legend leads with what covers the map"
    )
  }

  @Test
  fun `a continuous field reports no categories`() {
    val field = object : ScalarField {
      override val name = "height"
      override val palette = ElevationPalette()
      override fun valueAt(worldX: Double, worldY: Double) = worldX
    }

    val map = MapRenderer(config).render(field, Viewport.fit(config.worldBounds, 40, 40))

    assertTrue(map.categories.isEmpty(), "a metre is not a category and must not get a swatch list")
  }

  // ---- per-kind overlay filtering ---------------------------------------------------------------

  @Test
  fun `the overlay can be limited to some kinds without being turned off`() {
    val options = RenderOptions(featureKinds = setOf(FeatureKind.RIVER_CHANNEL))

    assertTrue(options.draws(FeatureKind.RIVER_CHANNEL))
    assertTrue(!options.draws(FeatureKind.BUILDING), "a kind outside the set must not be drawn")

    assertTrue(RenderOptions().draws(FeatureKind.BUILDING), "null means every kind, as it always did")
    assertTrue(!RenderOptions(features = false).draws(FeatureKind.RIVER_CHANNEL), "off still means off")
  }

  @Test
  fun `the census names only the kinds a world actually has, in declaration order`() {
    val store = storeOf(
      marker(1L, FeatureKind.SETTLEMENT, 1000.0, population = 5_000.0),
      marker(2L, FeatureKind.SETTLEMENT, 3000.0, population = 300.0),
      marker(3L, FeatureKind.ORE_DEPOSIT, 5000.0, population = null)
    )

    val census = sceneWith(store).featureCensus

    assertEquals(listOf(FeatureKind.ORE_DEPOSIT, FeatureKind.SETTLEMENT), census.keys.toList())
    assertEquals(2, census[FeatureKind.SETTLEMENT])
    assertNull(census[FeatureKind.BUILDING], "a kind this world has none of gets no legend row")
  }

  // ---- settlement dots ---------------------------------------------------------------------------

  @Test
  fun `a settlement is visible at whole-world zoom, and a city is bigger than a hamlet`() {
    // It used to be `drawRect(x, y, 0, 0)` at 38% alpha - one pixel, and the same pixel for a city of forty
    // thousand and a hamlet of twenty.
    val city = countMarkerPixels(population = 40_000.0)
    val hamlet = countMarkerPixels(population = 20.0)

    assertTrue(hamlet > 4, "a hamlet must still be findable, got $hamlet pixels")
    assertTrue(city > hamlet * 3, "a city ($city px) should read as far bigger than a hamlet ($hamlet px)")
  }

  @Test
  fun `a settlement with no population channel still gets a dot from its tier`() {
    val store = storeOf(
      PointMarker(
        id = FeatureId(9L),
        kind = FeatureKind.SETTLEMENT,
        position = Vec2d(4000.0, 4000.0),
        attributes = StationTable.Builder(1)
          .channel(SettlementChannels.TIER) { SettlementTier.CITY.ordinal.toDouble() }
          .build()
      )
    )

    assertTrue(markerPixels(store) > 4, "a world without history must still show where its towns are")
  }

  @Test
  fun `a settlement with no attributes at all is still drawn`() {
    // A pipeline that stops before placement, or a marker kind that is only ever a position.
    val store = storeOf(
      PointMarker(id = FeatureId(11L), kind = FeatureKind.SETTLEMENT, position = Vec2d(4000.0, 4000.0))
    )

    assertTrue(markerPixels(store) > 4, "no attributes must mean a default dot, not a crash or nothing")
  }

  // ---- helpers -----------------------------------------------------------------------------------

  private fun storeOf(vararg features: PointMarker) = FeatureStore().apply {
    add(StageId("test"), features.toList())
    freeze()
  }

  private fun marker(id: Long, kind: FeatureKind, at: Double, population: Double?) = PointMarker(
    id = FeatureId(id),
    kind = kind,
    position = Vec2d(at, at),
    attributes = StationTable.Builder(1)
      .channel(SettlementChannels.INDEX) { id.toDouble() }
      .channel(SettlementChannels.TIER) { SettlementTier.VILLAGE.ordinal.toDouble() }
      .apply { if (population != null) channel(SettlementChannels.POPULATION) { population } }
      .build()
  )

  private fun sceneWith(store: FeatureStore) = WorldScene(
    name = "test",
    config = config,
    fields = listOf(IntLayer(LayerId.BIOME, region, IntArray(64)).asField()),
    features = store
  )

  private fun countMarkerPixels(population: Double): Int =
    markerPixels(storeOf(marker(1L, FeatureKind.SETTLEMENT, 4000.0, population)))

  /** How many pixels of the whole-world view came out the settlement's colour. */
  private fun markerPixels(store: FeatureStore): Int {
    val scene = sceneWith(store)
    val view = Viewport.fit(config.worldBounds, 200, 200)
    val map = MapRenderer(config, scene::populationOf).render(
      scene.fields.first(), view, RenderOptions(hillshade = false), store.all()
    )

    val settlement = MapRenderer.colorOf(FeatureKind.SETTLEMENT).rgb and 0xFFFFFF
    var hits = 0
    for (y in 0 until map.image.height) {
      for (x in 0 until map.image.width) {
        if (map.image.getRGB(x, y) and 0xFFFFFF == settlement) hits++
      }
    }

    assertNotEquals(0, hits, "the settlement drew nothing at all")
    return hits
  }
}
