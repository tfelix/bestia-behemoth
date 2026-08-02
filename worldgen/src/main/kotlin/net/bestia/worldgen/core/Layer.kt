package net.bestia.worldgen.core

import kotlin.math.floor

/**
 * Name of a raster layer produced by a stage. Layer names are part of the dependency contract, so
 * they are declared, not conjured at the call site.
 */
data class LayerId(val name: String) {
  override fun toString() = name

  companion object {

    // --- Tectonics -------------------------------------------------------------------------------

    /**
     * The heightfield tectonics produces, before erosion has touched it.
     *
     * Deliberately not [ELEVATION]: the layer store refuses to let two stages write the same id, and
     * that refusal is load bearing. Erosion consumes this and produces [ELEVATION], so "the shape of
     * the land" and "the shape of the land before a hundred million years of rain" stay separable -
     * which is what lets a stage state which of the two it means.
     */
    val BEDROCK_ELEVATION = LayerId("bedrock_elevation")
    val PLATE_ID = LayerId("plate_id")

    /** 0 = weak mudstone, 1 = fresh granite. Erodibility is derived from it, not the other way round. */
    val ROCK_HARDNESS = LayerId("rock_hardness")
    val CRUST_AGE = LayerId("crust_age")

    /** Tectonic uplift rate in metres per timestep - the `U` of the stream power law. */
    val UPLIFT = LayerId("uplift")

    // --- Climate ---------------------------------------------------------------------------------

    /** Mean annual temperature in degrees Celsius. */
    val TEMPERATURE = LayerId("temperature")

    /** Summer-to-winter swing in degrees. Continentality; drives biome seasonality scoring. */
    val TEMPERATURE_RANGE = LayerId("temperature_range")

    /** Annual precipitation in millimetres. */
    val PRECIPITATION = LayerId("precipitation")

    /** 0 = evenly spread over the year, 1 = it all falls in one season. Monsoon versus maritime. */
    val PRECIPITATION_SEASONALITY = LayerId("precipitation_seasonality")

    /**
     * Precipitation in millimetres falling in one quarter of the year. The four sum to [PRECIPITATION].
     *
     * The seasonal advection passes have always run - [PRECIPITATION] is their sum - and until now their
     * output was summed and dropped, which is why the architecture document listed seasonal fields as the
     * cheapest unbuilt item on its list. These are that output, kept.
     *
     * **The names are northern-hemisphere labels for a phase of the orbit, not a claim about the weather
     * at a given cell.** A southern cell's summer is [PRECIPITATION_WINTER]: the belts migrate one way and
     * the hemispheres experience it oppositely, which is the whole point of storing four fields rather than
     * a scalar. [net.bestia.worldgen.climate.SeasonalPrecipitation] is the reader that knows this, and
     * anything wanting "the wet season here" should go through it rather than picking a layer by name.
     *
     * Monthly figures are **not** stored. Twelve layers would be twelve times the memory for a curve that
     * four control points already describe; `SeasonalPrecipitation.atMonth` interpolates them periodically
     * on demand.
     */
    val PRECIPITATION_SPRING = LayerId("precipitation_spring")

    /** See [PRECIPITATION_SPRING]. Northern summer; the southern hemisphere's winter. */
    val PRECIPITATION_SUMMER = LayerId("precipitation_summer")

    /** See [PRECIPITATION_SPRING]. Northern autumn. */
    val PRECIPITATION_AUTUMN = LayerId("precipitation_autumn")

    /** See [PRECIPITATION_SPRING]. Northern winter; the southern hemisphere's summer. */
    val PRECIPITATION_WINTER = LayerId("precipitation_winter")

    /** Metres to the nearest ocean cell. Computed once by climate; wanted by half the pipeline. */
    val DISTANCE_TO_OCEAN = LayerId("distance_to_ocean")

    // --- Erosion ---------------------------------------------------------------------------------

    /**
     * The fluvial surface: bedrock after stream power, mass wasting and deposition, before ice.
     *
     * Deliberately **not** the layer downstream stages read. Erosion is not the last thing that shapes the
     * ground - the glacial stage carves troughs and cirques hundreds of metres into it - so a stage reading
     * this one is reading a surface that is not the final answer anywhere ice went. [ELEVATION] is that
     * answer, and it is glacial's to produce.
     *
     * The only legitimate readers are [GlacialStage][net.bestia.worldgen.geo.GlacialStage], which carves it,
     * and tooling that wants to *show* what ice did by differencing the two.
     */
    val ERODED_ELEVATION = LayerId("eroded_elevation")

    /**
     * The land surface everything downstream means when it says "elevation".
     *
     * Produced by the **glacial** stage, not by erosion, and that is load bearing rather than a curiosity of
     * the wiring. It is the last word on the ground, so every stage that decides anything about where things
     * sit - flow routing, habitability, settlement placement, town layout - reads it and therefore has to
     * declare a dependency reaching glacial. When erosion owned it, nothing did: the four glacial feature
     * kinds carved the chunk ground at materialisation time and every civ stage had already committed to a
     * surface without them in it, which put buildings on 500 m plinths.
     */
    val ELEVATION = LayerId("elevation")

    /** Thickness of deposited sediment in metres: alluvium, valley fill, deltas. */
    val SEDIMENT = LayerId("sediment")

    // --- Hydrology -------------------------------------------------------------------------------

    /** D8 index into [net.bestia.worldgen.fields.D8], or -1 where the cell drains out of the world. */
    val FLOW_DIRECTION = LayerId("flow_direction")

    /** Upslope contributing area in square metres. */
    val FLOW_ACCUMULATION = LayerId("flow_accumulation")

    /** Mean discharge in cubic metres per second - accumulation weighted by local precipitation. */
    val DISCHARGE = LayerId("discharge")

    /** Surface elevation of standing water, or NaN where there is none. Sea, lakes, reservoirs. */
    val WATER_LEVEL = LayerId("water_level")

    /** Basin label, 0 for none. Negative labels mark endorheic basins - salt lakes. */
    val LAKE_ID = LayerId("lake_id")

    // --- Biomes ----------------------------------------------------------------------------------

    val BIOME = LayerId("biome")

    /**
     * How well the chosen biome fit, as a **percentile rank** over this world's own cells: 0 in the most
     * transitional cell in the world, 1 where nothing came close, and 1 wherever there is no runner-up at all.
     *
     * A rank rather than the classifier's raw score, and the distinction is the whole usability of the layer.
     * The raw `1 - sqrt(best/second)` is a correct *ordering* and not a fraction of anything - with fourteen
     * prototypes in seven dimensions it measured 0.069 at the median - so a consumer treating it as a mixing
     * weight mixes the wrong cells. Ranked, `1 - confidence` is a share by construction and needs no
     * per-consumer rescaling; `voxel/SurfaceSampler.kt` deleted exactly such a compensation when this changed.
     *
     * The cost is that it is **relative to a world**, not absolute: the same cell in a smaller world with
     * different neighbours would rank differently. That is the right trade for a blend weight and the wrong one
     * for a threshold, so do not compare it across worlds. See `BiomeStage.rankConfidence`.
     */
    val BIOME_CONFIDENCE = LayerId("biome_confidence")

    /**
     * The biome that came *second* in the classification, or [NO_SECONDARY] where there was none.
     *
     * With [BIOME] this says what a cell is a transition *between*, which [BIOME_CONFIDENCE] alone cannot:
     * confidence says how much of a transition a cell is in and this says to what. The pair is what lets a
     * consumer dither a boundary instead of drawing a line across it.
     *
     * There is deliberately no separate blend-weight layer. [BIOME_CONFIDENCE] already is one - a monotone
     * function of the two scores' ratio - and storing a second raster that is a function of one already on
     * disk is a raster that can disagree with itself.
     *
     * **Read it with `Biome.entries.getOrNull`, never `Biome.of`.** `of` *coerces* an out-of-range ordinal
     * into the enum, so the [NO_SECONDARY] sentinel would come back as the last entry - `CLIFF` - and a cell
     * with no runner-up would confidently claim to be half cliff.
     */
    val BIOME_SECONDARY = LayerId("biome_secondary")

    /**
     * The [BIOME_SECONDARY] value meaning "this cell has no runner-up".
     *
     * Negative so it cannot collide with an appended [net.bestia.worldgen.bio.Biome] ordinal, which is the
     * one thing a sentinel in an on-disk enum raster has to guarantee.
     */
    const val NO_SECONDARY = -1
    val SOIL_FERTILITY = LayerId("soil_fertility")

    /** Depth of soil over bedrock in metres. Thin on crests and steep ground, deep in valley floors. */
    val SOIL_DEPTH = LayerId("soil_depth")

    // --- Vegetation ------------------------------------------------------------------------------

    /**
     * Share of the ground under a leaf canopy, 0 to 1.
     *
     * **The only thing about vegetation that is stored anywhere.** A world of five hundred kilometres holds
     * on the order of a billion trees, so the trees themselves are implicit - a function from a position to
     * whether something grows there, evaluated per column at chunk generation and never written down. This
     * is the kilometre-scale summary of that function, and it exists because "how wooded is it here" is a
     * question stages and spawners ask about places they are not materialising.
     *
     * Averaged from the *same* function `voxel/VegetationScatter.kt` plants from, over sub-samples of each
     * cell, so the raster and the voxels cannot drift apart - the alternative, a second density model at the
     * raster tier, is two things that mean the same and disagree.
     */
    val CANOPY_COVER = LayerId("canopy_cover")

    // --- Glaciation ------------------------------------------------------------------------------

    /**
     * Ice thickness in metres at the glacial maximum.
     *
     * The coarse pass of glacial erosion, which decides *where* glaciers are rather than what they look like -
     * the shape of a trough is a vector feature, because a kilometre cell cannot hold one.
     */
    val ICE_THICKNESS = LayerId("ice_thickness")

    // --- Resources -------------------------------------------------------------------------------

    /**
     * Extractable value within reach, 0 to 1.
     *
     * A smoothed field rather than the deposits themselves, because what settlement placement wants to know
     * is "is there anything worth having near here" - and answering that from the deposit index would be a
     * spatial query per cell.
     */
    val RESOURCE_VALUE = LayerId("resource_value")

    // --- Civilisation ----------------------------------------------------------------------------

    /** Suitability for settlement, 0 to 1. Weighted differently per culture; see HabitabilityStage. */
    val HABITABILITY = LayerId("habitability")

    /** Cost of moving one metre across a cell, relative to easy flat ground. Drives road routing. */
    val MOVEMENT_COST = LayerId("movement_cost")
  }
}

sealed interface LayerData {
  val id: LayerId
  val region: CellRegion
}

/**
 * A dense scalar raster over a [CellRegion].
 *
 * Stored as a flat array in row-major order. Sampling takes world coordinates in metres rather than
 * cell indices, because everything downstream of the raster tier thinks in world space - only the
 * raster itself knows what its cell size is.
 */
class FloatLayer(
  override val id: LayerId,
  override val region: CellRegion,
  val data: FloatArray
) : LayerData {

  init {
    require(data.size.toLong() == region.cellCount) {
      "Layer $id has ${data.size} values but region $region has ${region.cellCount} cells"
    }
  }

  operator fun get(x: Int, y: Int): Float {
    val cx = x.coerceIn(region.minX, region.maxX)
    val cy = y.coerceIn(region.minY, region.maxY)
    return data[(cy - region.minY) * region.width + (cx - region.minX)]
  }

  operator fun set(x: Int, y: Int, value: Float) {
    require(region.contains(x, y)) { "($x,$y) is outside $region" }
    data[(y - region.minY) * region.width + (x - region.minX)] = value
  }

  /** Bilinear sample at a world position in metres. Cheap, C0, good enough for most fields. */
  fun sampleBilinear(worldX: Double, worldY: Double): Double {
    val fx = worldX / region.resolution.metresPerCell - 0.5
    val fy = worldY / region.resolution.metresPerCell - 0.5
    val x0 = floor(fx).toInt()
    val y0 = floor(fy).toInt()
    val tx = fx - x0
    val ty = fy - y0

    val v00 = this[x0, y0].toDouble()
    val v10 = this[x0 + 1, y0].toDouble()
    val v01 = this[x0, y0 + 1].toDouble()
    val v11 = this[x0 + 1, y0 + 1].toDouble()

    val bottom = v00 + (v10 - v00) * tx
    val top = v01 + (v11 - v01) * tx

    return bottom + (top - bottom) * ty
  }

  /**
   * Bicubic (Catmull-Rom) sample at a world position in metres.
   *
   * This is what chunk generation uses to lift the coarse world map into a chunk: bilinear leaves
   * visible facets where the 1 km cells meet, and those facets are exactly the kind of grid artefact
   * the whole three-representation split exists to avoid.
   */
  fun sampleBicubic(worldX: Double, worldY: Double): Double {
    val fx = worldX / region.resolution.metresPerCell - 0.5
    val fy = worldY / region.resolution.metresPerCell - 0.5
    val x0 = floor(fx).toInt()
    val y0 = floor(fy).toInt()
    val tx = fx - x0
    val ty = fy - y0

    val rows = DoubleArray(4)
    for (j in -1..2) {
      rows[j + 1] = catmullRom(
        this[x0 - 1, y0 + j].toDouble(),
        this[x0, y0 + j].toDouble(),
        this[x0 + 1, y0 + j].toDouble(),
        this[x0 + 2, y0 + j].toDouble(),
        tx
      )
    }

    return catmullRom(rows[0], rows[1], rows[2], rows[3], ty)
  }

  fun copy() = FloatLayer(id, region, data.copyOf())

  override fun toString() = "FloatLayer[$id, $region]"

  companion object {
    fun filled(id: LayerId, region: CellRegion, value: Float = 0f) =
      FloatLayer(id, region, FloatArray(region.cellCount.toInt()) { value })

    private fun catmullRom(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
      val t2 = t * t
      val t3 = t2 * t
      return 0.5 * (
          2.0 * p1 +
              (-p0 + p2) * t +
              (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
              (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
          )
    }
  }
}

/**
 * A dense integer raster - plate ids, biome ids, D8 flow directions.
 *
 * Deliberately has no interpolating sampler: interpolating a category id is meaningless, and
 * offering the method would guarantee somebody eventually did it.
 */
class IntLayer(
  override val id: LayerId,
  override val region: CellRegion,
  val data: IntArray
) : LayerData {

  init {
    require(data.size.toLong() == region.cellCount) {
      "Layer $id has ${data.size} values but region $region has ${region.cellCount} cells"
    }
  }

  operator fun get(x: Int, y: Int): Int {
    val cx = x.coerceIn(region.minX, region.maxX)
    val cy = y.coerceIn(region.minY, region.maxY)
    return data[(cy - region.minY) * region.width + (cx - region.minX)]
  }

  operator fun set(x: Int, y: Int, value: Int) {
    require(region.contains(x, y)) { "($x,$y) is outside $region" }
    data[(y - region.minY) * region.width + (x - region.minX)] = value
  }

  /** Nearest-cell lookup at a world position in metres. */
  fun sampleNearest(worldX: Double, worldY: Double): Int {
    val cx = floor(worldX / region.resolution.metresPerCell).toInt()
    val cy = floor(worldY / region.resolution.metresPerCell).toInt()
    return this[cx, cy]
  }

  fun copy() = IntLayer(id, region, data.copyOf())

  override fun toString() = "IntLayer[$id, $region]"

  companion object {
    fun filled(id: LayerId, region: CellRegion, value: Int = 0) =
      IntLayer(id, region, IntArray(region.cellCount.toInt()) { value })
  }
}
