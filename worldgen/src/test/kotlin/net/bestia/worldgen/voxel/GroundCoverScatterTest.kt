package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plants per hectare, per biome, and the things a plant may never do.
 *
 * Counted output rather than a "some exist" check, on the habit [GroundCoverScatter]'s own note insists on: a
 * `litterGain` an order of magnitude too low puts a handful of herbs in a whole world and every structural
 * test still passes, and one too high carpets a meadow in entities. Neither is visible without counting, so
 * the census prints its numbers and the assertions are wide tripwires around them.
 *
 * One generated world, shared, and the chunk sweeps are the expensive half - so the whole sample is swept
 * once into [plants] and every test reads that.
 */
class GroundCoverScatterTest {

  private val world: GeneratedWorld by lazy {
    StandardWorld.build(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  /** One emitted plant, copied out of the struct-of-arrays so the sweeps can be shared. */
  private class Plant(
    val kind: PropKind,
    val propId: Long,
    val x: Double,
    val y: Double,
    val ground: Double,
    val height: Double,
    val chunkX: Int,
    val chunkY: Int
  )

  @Test
  fun `nine prop kinds still fit a prop id`() {
    // Four bits of kind, and PropId's own `init` is what fails at seventeen. What this adds is that the three
    // appended kinds round-trip rather than aliasing onto an existing one.
    assertTrue(PropKind.entries.size <= 16, "${PropKind.entries.size} kinds no longer fit four bits")

    val names = mutableSetOf<Long>()
    for (kind in PropKind.entries) {
      for ((cellX, cellY) in listOf(0L to 0L, 1L to -1L, -536_870_912L to 536_870_911L)) {
        val id = PropId.of(kind, cellX, cellY)
        assertEquals(kind, PropId.kindOf(id))
        assertEquals(cellX, PropId.cellXOf(id))
        assertEquals(cellY, PropId.cellYOf(id))
        assertTrue(names.add(id), "$kind at ($cellX, $cellY) collides with another prop's name")
      }
    }
  }

  /**
   * What each biome actually grows.
   *
   * Two things here are deliberately loose rather than exact.
   *
   * The **desert** is asserted as a ratio and not as zero. Desert litter is 0.02, so the rate is near enough
   * nothing but not identically nothing, and asserting an exact zero would assert something the code does not
   * do. Which ground counts as desert at all is [hectaresOf]'s problem, and it is the interesting one.
   *
   * A **biome this world happens not to have** is skipped and named rather than failed. Which biomes a seed
   * grows is a lottery - this one has no bog at all - and a census that fails on that is a census that has to
   * be retuned every time anything upstream reshuffles the map.
   */
  @Test
  fun `the biome census reads sensibly`() {
    val census = census()

    println("plants per hectare over ${sweptChunks.size} chunks, by the biome under each plant:")
    println("  %-22s %9s %8s %8s %8s %8s".format("biome", "hectares", "herb", "shrub", "reed", "total"))
    for (biome in Biome.entries) {
      val rate = census[biome] ?: continue
      println(
        "  %-22s %9.1f %8.1f %8.1f %8.1f %8.1f".format(
          biome.name,
          hectaresOf.getValue(biome),
          rate.getValue(PropKind.HERB),
          rate.getValue(PropKind.SHRUB),
          rate.getValue(PropKind.REED),
          rate.values.sum()
        )
      )
    }
    if (absent.isNotEmpty()) println("  not on this world, so not swept: ${absent.sorted().joinToString()}")

    // The biomes the assertions read. Without them the census measures nothing and would pass empty.
    for (biome in REQUIRED_BIOMES) {
      assertTrue(census[biome] != null, "$biome is not on this world, so the census proves nothing")
    }

    val grassland = census.getValue(Biome.GRASSLAND)
    val forest = census.getValue(Biome.TEMPERATE_FOREST)
    val desert = census.getValue(Biome.DESERT).values.sum()

    // A meadow with nothing in it is a forage mechanic nobody meets; a plant every two metres is a thousand
    // entities in a view volume. Wide bounds either side of the measured figure.
    assertTrue(
      grassland.values.sum() in 10.0..400.0,
      "grassland grows %.1f plants per hectare".format(grassland.values.sum())
    )
    assertTrue(
      desert < grassland.values.sum() * 0.05,
      "the desert grows %.1f against grassland's %.1f".format(desert, grassland.values.sum())
    )

    assertTrue(
      grassland.getValue(PropKind.HERB) > grassland.getValue(PropKind.SHRUB),
      "a meadow comes out woodier than it is herbaceous: $grassland"
    )
    assertTrue(
      forest.getValue(PropKind.SHRUB) > forest.getValue(PropKind.HERB),
      "a forest floor comes out herbaceous rather than shrubby: $forest"
    )
    assertTrue(forest.values.sum() > 0.0, "a closed wood has a bare floor; canopy damping has become a gate")
  }

  /**
   * Ice, snow and standing water carry nothing.
   *
   * Recomputed at each emitted plant rather than trusted, because that veto is the one place this scatter can
   * disagree with the block the voxel pass puts under the plant - both call `SurfaceCover.cap`, so a
   * disagreement is a bug in one of them.
   */
  @Test
  fun `no plant stands on ice or snow, and none in standing water`() {
    val surface = world.materializer.surface
    assertTrue(plants.isNotEmpty(), "the sweep found no plants at all, so this proves nothing")

    for (plant in plants) {
      assertTrue(
        surface.waterLevelAt(plant.x, plant.y) <= plant.ground,
        "${plant.kind} at (${plant.x}, ${plant.y}) is under water"
      )

      val cap = SurfaceCover.cap(
        surface.biomeAt(plant.x, plant.y),
        surface.temperatureAt(plant.x, plant.y),
        0.0,
        surface.isBlightedAt(plant.x, plant.y)
      )
      assertTrue(
        cap != BlockType.ICE && cap != BlockType.SNOW,
        "${plant.kind} at (${plant.x}, ${plant.y}) is rooted in $cap"
      )
    }

    println("${plants.size} plants checked for ice, snow and standing water")
  }

  /**
   * A reed stands within `reedMargin` of the water, and everything else stands above it.
   *
   * Both directions matter and only together: the first alone passes on a world with no reeds in it, and the
   * second alone passes if every plant is a reed.
   *
   * Swept over lake and sea shores, because those are the only water surfaces this scatter can see -
   * `SurfaceSampler.waterLevelAt` covers lakes and the sea, and a river's surface comes from the vector tier.
   * So there are no reeds on a river bank today, which is the gap [GroundCoverParams.reedMargin] records.
   */
  @Test
  fun `a reed only stands at the water's edge, and nothing else does`() {
    val surface = world.materializer.surface
    val margin = world.params.groundCover.reedMargin

    val reeds = shorePlants.filter { it.kind == PropKind.REED }
    assertTrue(reeds.isNotEmpty(), "no reeds on any shore, so this test proves nothing")

    for (plant in shorePlants) {
      val freeboard = plant.ground - surface.waterLevelAt(plant.x, plant.y)
      if (plant.kind == PropKind.REED) {
        assertTrue(freeboard <= margin, "a reed stands %.2f m above the water".format(freeboard))
      } else {
        assertTrue(freeboard > margin, "a ${plant.kind} stands %.2f m above the water".format(freeboard))
      }
    }

    println("${reeds.size} reeds of ${shorePlants.size} shore plants, at a margin of $margin m")
  }

  /**
   * Every plant is claimed by exactly one chunk.
   *
   * The seam property, and it is not vacuous even though `propsIn` filters on `config.chunkOf`: the cell range
   * a chunk walks overlaps its neighbours', so a plant in a boundary cell is *visited* by two chunks and must
   * be emitted by one. A closed-interval bounds test in place of the integer one would emit it twice.
   */
  @Test
  fun `no plant is claimed by two chunks`() {
    val owners = HashMap<Long, Plant>()

    for (plant in plants) {
      val previous = owners.put(plant.propId, plant)
      assertTrue(
        previous == null,
        "prop ${plant.propId} is claimed by chunk (${plant.chunkX}, ${plant.chunkY}) and " +
            "(${previous?.chunkX}, ${previous?.chunkY})"
      )
    }

    assertEquals(plants.size, owners.size)
    println("${owners.size} distinct plants over the whole sweep")
  }

  @Test
  fun `a plant is the size its kind is`() {
    val params = world.params.groundCover

    for (plant in plants) {
      val nominal = when (plant.kind) {
        PropKind.HERB -> params.herbHeight
        PropKind.SHRUB -> params.shrubHeight
        PropKind.REED -> params.reedHeight
        else -> error("${plant.kind} is not ground cover")
      }

      val lowest = nominal * (1.0 - params.heightSpread) - TOLERANCE
      val tallest = nominal * (1.0 + params.heightSpread) + TOLERANCE
      assertTrue(
        plant.height in lowest..tallest,
        "a ${plant.kind} is %.2f m against a nominal %.2f".format(plant.height, nominal)
      )
    }
  }

  /** Biomes this world does not have, so the census can report them rather than fail on them. */
  private val absent = mutableSetOf<String>()

  /**
   * The chunks every test sweeps: several samples of each census biome, plus the shores.
   *
   * A **set**, and that is not tidiness. The sample regions are 160 m blocks chosen independently, so two of
   * them overlap wherever two biomes meet - and a chunk swept twice yields the same plant twice, which reads
   * as a prop claimed by two chunks. The first version of this file failed its own seam test that way.
   */
  private val sweptChunks: Set<Pair<Int, Int>> by lazy {
    val chunks = LinkedHashSet<Pair<Int, Int>>()
    for (origins in CENSUS_BIOMES.map { originsOf(it) } + listOf(shoreOrigins())) {
      for ((originChunkX, originChunkY) in origins) {
        for (offsetY in 0 until SAMPLE_CHUNKS) {
          for (offsetX in 0 until SAMPLE_CHUNKS) {
            chunks.add((originChunkX + offsetX) to (originChunkY + offsetY))
          }
        }
      }
    }
    chunks
  }

  /** Every plant in [sweptChunks]. Swept once; every test reads this. */
  private val plants: List<Plant> by lazy {
    val out = ArrayList<Plant>()

    for ((chunkX, chunkY) in sweptChunks) {
      val props = world.propsIn(chunkX, chunkY)

      for (i in props.indices) {
        if (props.kindAt(i) !in GroundCoverScatter.KINDS) continue
        out.add(
          Plant(
            kind = props.kindAt(i),
            propId = props.identityAt(i),
            x = props.xAt(i),
            y = props.yAt(i),
            ground = props.groundAt(i),
            height = props.heightAt(i),
            chunkX = chunkX,
            chunkY = chunkY
          )
        )
      }
    }

    out
  }

  /** The plants standing on a lake or sea shore, for the reed test. */
  private val shorePlants: List<Plant> by lazy {
    plants.filter { it.ground - world.materializer.surface.waterLevelAt(it.x, it.y) <= SHORE_BAND }
  }

  /**
   * Hectares of each biome inside [sweptChunks], measured on a grid rather than assumed.
   *
   * A sample block is chosen by the biome at one cell centre, but `SurfaceSampler.biomeAt` dithers between a
   * cell's winner and its runner-up **across the whole cell** where the classifier's confidence is low - not
   * only near a boundary. So a "desert" block genuinely contains grassland, and dividing its plant count by
   * its whole area attributes those plants to the desert. The first version of this read 15.5 plants per
   * hectare of desert for exactly that reason.
   *
   * Binning both the plants and the ground by the biome under them is what makes the rate a rate.
   */
  private val hectaresOf: Map<Biome, Double> by lazy {
    val surface = world.materializer.surface
    val extent = world.config.chunkExtent
    val step = extent / AREA_SAMPLES_PER_AXIS
    val hectaresPerSample = step * step / 10_000.0

    val out = HashMap<Biome, Double>()
    for ((chunkX, chunkY) in sweptChunks) {
      for (sampleY in 0 until AREA_SAMPLES_PER_AXIS) {
        for (sampleX in 0 until AREA_SAMPLES_PER_AXIS) {
          val worldX = (chunkX * extent) + (sampleX + 0.5) * step
          val worldY = (chunkY * extent) + (sampleY + 0.5) * step
          val biome = surface.biomeAt(worldX, worldY)
          out[biome] = (out[biome] ?: 0.0) + hectaresPerSample
        }
      }
    }

    out
  }

  /** Plants per hectare per kind, per biome, for every biome with enough ground swept to mean anything. */
  private fun census(): Map<Biome, Map<PropKind, Double>> {
    val surface = world.materializer.surface
    val counted = HashMap<Biome, MutableMap<PropKind, Int>>()

    for (plant in plants) {
      val biome = surface.biomeAt(plant.x, plant.y)
      val perKind = counted.getOrPut(biome) { HashMap() }
      perKind[plant.kind] = (perKind[plant.kind] ?: 0) + 1
    }

    return hectaresOf
      .filterValues { it >= MIN_HECTARES }
      .mapValues { (biome, hectares) ->
        GroundCoverScatter.KINDS.associateWith { kind ->
          (counted[biome]?.get(kind) ?: 0) / hectares
        }
      }
  }

  /**
   * South-west sample chunks on ground the scatter itself calls this biome, spread by stride.
   *
   * By stride rather than off the front, for the reason `CrystalScatterTest.sampleOrigins` records: a slice
   * from one end of the list re-measures a different patch of the world whenever anything upstream adds or
   * removes a cell from the candidate set.
   *
   * The biome comes from `SurfaceSampler.biomeAt` rather than off `LayerId.BIOME` directly, and that is not
   * incidental: the biome raster is coarser than the elevation grid and `IntLayer.get` **coerces** an
   * out-of-region coordinate rather than refusing it, so indexing it with a fine-grid cell silently returns
   * the raster's edge. Going through the sampler also means the census reads the same function the scatter does.
   */
  private fun originsOf(biome: Biome): List<Pair<Int, Int>> {
    return originsWhere(biome.name) { index, worldX, worldY ->
      isDryLand(index) && world.materializer.surface.biomeAt(worldX, worldY) == biome
    }
  }

  /**
   * Sample chunks on the shore of a lake or the sea: dry cells with water in the next cell over.
   *
   * The only ground where `waterLevelAt` has a surface for a reed to stand beside - see the reed test.
   */
  private fun shoreOrigins(): List<Pair<Int, Int>> {
    val region = elevation.region

    return originsWhere("shore") { index, _, _ ->
      val cellX = region.minX + index % region.width
      val cellY = region.minY + index / region.width
      isDryLand(index) && NEIGHBOURS.any { (dx, dy) -> isWater(cellX + dx, cellY + dy) }
    }
  }

  /**
   * Sample chunks around the cells of the elevation grid that [qualifies], by flat index and cell centre.
   *
   * Too few qualifying cells is recorded in [absent] and returns nothing, rather than throwing. A test that
   * needs the sample to be non-empty says so itself, which is the difference between "this world has no bog"
   * and "the sweep found no plants".
   */
  private fun originsWhere(what: String, qualifies: (Int, Double, Double) -> Boolean): List<Pair<Int, Int>> {
    val region = elevation.region
    val metres = world.config.baseResolution.metresPerCell

    val qualifying = ArrayList<Pair<Double, Double>>()
    for (index in elevation.data.indices) {
      val centreX = (region.minX + index % region.width + 0.5) * metres
      val centreY = (region.minY + index / region.width + 0.5) * metres
      if (qualifies(index, centreX, centreY)) qualifying.add(centreX to centreY)
    }

    if (qualifying.size < SAMPLE_SQUARES) {
      absent.add(what)
      return emptyList()
    }

    val stride = qualifying.size / SAMPLE_SQUARES
    return (0 until SAMPLE_SQUARES).map { qualifying[it * stride] }.map { (centreX, centreY) ->
      // Centred on the cell: a block anchored at its corner spends most of its area in the neighbouring cell,
      // so it would measure the boundary rather than the biome.
      val half = SAMPLE_CHUNKS / 2
      (world.config.chunkOf(centreX) - half) to (world.config.chunkOf(centreY) - half)
    }
  }

  private val elevation: FloatLayer by lazy { world.world.layers.require(LayerId.ELEVATION) }
  private val waterLevel: FloatLayer by lazy { world.world.layers.require(LayerId.WATER_LEVEL) }

  private fun isDryLand(index: Int): Boolean {
    return elevation.data[index] > world.config.seaLevel && waterLevel.data[index].isNaN()
  }

  private fun isWater(cellX: Int, cellY: Int): Boolean {
    val region = elevation.region
    if (cellX < region.minX || cellY < region.minY) return false
    if (cellX >= region.minX + region.width || cellY >= region.minY + region.height) return false

    val index = (cellY - region.minY) * region.width + (cellX - region.minX)
    return elevation.data[index] <= world.config.seaLevel || !waterLevel.data[index].isNaN()
  }

  private companion object {

    /**
     * Sample cells per biome. Twelve at five chunks square is about three hectares per biome, so a rate of
     * tens per hectare is counted from hundreds of plants rather than from a handful.
     */
    const val SAMPLE_SQUARES = 12

    /** Chunks per axis around each sample cell. Five at 32 m is 160 m, well inside a kilometre cell. */
    const val SAMPLE_CHUNKS = 5

    /** Slack on a height comparison, for the float the prop arrays store. */
    const val TOLERANCE = 1e-3

    /** Area samples per chunk axis. Eight at 32 m is one every four metres, finer than the biome dither. */
    const val AREA_SAMPLES_PER_AXIS = 8

    /** Ground a biome needs inside the sweep before its rate is reported rather than being noise. */
    const val MIN_HECTARES = 1.0

    /**
     * How far above the water a plant counts as being on the shore, in metres.
     *
     * Comfortably wider than `reedMargin`, because the reed test needs the plants that are *near* the water
     * and not reeds as well as the reeds themselves - that is the half of it which catches a margin applied
     * the wrong way round.
     */
    const val SHORE_BAND = 4.0

    /**
     * The biomes the census reports, chosen to exercise each term in the density separately: grassland is high
     * litter under open sky, temperate forest is high litter under a closed canopy, dryland is the scrub the
     * shrub share is tuned against, bog outscores grassland on litter while being nearly treeless, and the
     * desert is the floor.
     */
    val CENSUS_BIOMES = listOf(
      Biome.GRASSLAND,
      Biome.TEMPERATE_FOREST,
      Biome.DRYLAND,
      Biome.BOG,
      Biome.DESERT
    )

    /** The three the assertions read. A world without these is not a world this census can say anything about. */
    val REQUIRED_BIOMES = listOf(Biome.GRASSLAND, Biome.TEMPERATE_FOREST, Biome.DESERT)

    val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
  }
}
