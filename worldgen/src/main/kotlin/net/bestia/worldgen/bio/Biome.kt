package net.bestia.worldgen.bio

import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import kotlin.math.sqrt

/**
 * The biome vocabulary.
 *
 * Ordinals are written into the [net.bestia.worldgen.core.LayerId.BIOME] raster and therefore into
 * chunk cache keys, so **entries may be appended but not reordered or removed** once a world has been
 * generated. That is the same constraint as any other on-disk enum, and it is worth stating here rather
 * than discovering it when an existing world's biomes all shift by one.
 */
enum class Biome(
  val label: String,

  /**
   * How much dead organic matter this biome puts into its soil, 0 to 1.
   *
   * A **fertility** term - `SOIL_FERTILITY` is `0.20 * litter + ...` - and not a measure of trees, which is
   * why [canopy] exists beside it rather than being derived from it. Grassland is one of the most productive
   * litter producers there is and has almost no trees on it; the two numbers differ by a factor of fourteen
   * here and reusing one for the other would have planted a forest on every prairie in the world.
   */
  val litter: Double,

  /**
   * Chance that a four-metre patch of mature ground in this biome holds a tree, 0 to 1.
   *
   * The per-biome term in `voxel/VegetationScatter.kt`'s density, and therefore in `LayerId.CANOPY_COVER`.
   * A *probability per lattice cell*, not a cover fraction: crowns overlap, so the share of ground actually
   * shaded is lower and is computed from this - see `VegetationScatter.coverAt`.
   *
   * Sparse values are the interesting ones. Savanna at 0.14 is what makes it savanna rather than either
   * grassland or forest, and grassland at 0.05 is the lone tree in the middle of a field that gives a
   * landscape its scale.
   */
  val canopy: Double
) {

  //     biome                                       litter  canopy
  // Water.
  OCEAN("ocean", 0.0, 0.0),
  LAKE("lake", 0.0, 0.0),

  // Cold.
  ICE_SHEET("ice sheet", 0.0, 0.0),
  GLACIER("glacier", 0.0, 0.0),
  TUNDRA("tundra", 0.25, 0.02),
  TAIGA("taiga", 0.45, 0.70),
  COLD_DESERT("cold desert", 0.05, 0.0),
  // Krummholz: a few stunted things at the tree line and nothing above it.
  ALPINE("alpine", 0.2, 0.04),

  // Temperate.
  TEMPERATE_FOREST("temperate forest", 0.85, 0.80),
  TEMPERATE_RAINFOREST("temperate rainforest", 0.9, 0.85),
  GRASSLAND("grassland", 0.7, 0.05),
  STEPPE("steppe", 0.35, 0.02),
  SHRUBLAND("shrubland", 0.3, 0.10),

  // Warm.
  DESERT("desert", 0.02, 0.0),
  SAVANNA("savanna", 0.4, 0.14),
  TROPICAL_SEASONAL_FOREST("tropical seasonal forest", 0.75, 0.65),
  TROPICAL_RAINFOREST("tropical rainforest", 0.6, 0.90),

  // Edge biomes: driven by adjacency to something rather than by climate.
  WETLAND("wetland", 0.8, 0.20),
  // Gallery forest: the band of trees along a watercourse in country that has none away from it.
  RIPARIAN("riparian", 0.8, 0.60),
  BEACH("beach", 0.05, 0.0),
  BADLANDS("badlands", 0.05, 0.0),
  CLIFF("cliff", 0.0, 0.0);

  val isWater get() = this == OCEAN || this == LAKE

  companion object {
    fun of(ordinal: Int): Biome = entries[ordinal.coerceIn(0, entries.size - 1)]
  }
}

/**
 * The axes of the classification space.
 *
 * Named indices into a flat array rather than a data class, because the scoring loop runs once per cell
 * per prototype - twenty-odd million times on a full-size world - and allocating a feature vector each
 * time would dominate the stage.
 */
object BiomeAxis {
  const val TEMPERATURE = 0
  const val PRECIPITATION = 1
  const val SEASONALITY = 2
  const val TEMPERATURE_RANGE = 3
  const val ELEVATION = 4
  const val SLOPE = 5
  const val WETNESS = 6
  const val COUNT = 7
}

/** One biome as a point in the classification space, with per-axis importance. */
class BiomePrototype(
  val biome: Biome,
  val at: DoubleArray,
  val weight: DoubleArray
) {
  init {
    require(at.size == BiomeAxis.COUNT && weight.size == BiomeAxis.COUNT) {
      "A prototype needs ${BiomeAxis.COUNT} values per array"
    }
  }
}

/**
 * The best match, how clearly it won, and what came second.
 *
 * [runnerUp] is null only when there was nothing to come second - a single-prototype classification. It is
 * *not* null merely because the winner was clear: a confident cell still has a nearest neighbour, and
 * [confidence] is the thing that says how little it matters.
 */
class BiomeMatch(val biome: Biome, val confidence: Double, val runnerUp: Biome? = null)

/**
 * The scales that turn a cell's climate into the unit cube the classifier measures distances in.
 *
 * Hoisted out of [Biomes.describe], where they were seven bare literals inside the arithmetic. They are
 * tunables in every sense that matters - the precipitation cap decides how much of the dry end of the range
 * the classifier can even see, and the slope cap decides at what gradient everything reads as cliff - and
 * while they sat inline they could not be fingerprinted, so changing one moved every biome boundary in the
 * world and no version number noticed.
 *
 * A `data class` with a default instance rather than constants, so [Biomes.catalogueDigest] can fold it and
 * `ParamsFields` can check that all of it is folded. Threading it through `BiomeParams` as a caller-supplied
 * value is the obvious next step and is deliberately not taken yet: [Biomes.describe] is called per cell from
 * more than one stage, and a parameter nobody varies is a parameter list nobody reads.
 */
data class BiomeAxisRanges(
  /** Degrees added before scaling, so the coldest ground the model produces lands at zero rather than below. */
  val temperatureOffset: Double = 25.0,
  /** Degrees of span mapped onto the unit interval. */
  val temperatureSpan: Double = 65.0,
  /**
   * Millimetres of annual rainfall mapped onto the unit interval, **square-root scaled**.
   *
   * The scaling is the point: the boundaries that matter are crowded into the dry end, where 200 against
   * 500 mm decides desert against grassland, while 2500 against 2800 decides nothing.
   */
  val precipitationCap: Double = 4_000.0,
  /** Degrees of annual temperature range mapped onto the unit interval. */
  val temperatureRangeCap: Double = 45.0,
  /** Metres above sea level mapped onto the unit interval. */
  val elevationCap: Double = 4_000.0,
  /** Ground gradient mapped onto the unit interval. Above this everything reads as cliff. */
  val slopeCap: Double = 0.35
) : Params {

  override fun digest() = ParamsDigest()
    .put("temperatureOffset", temperatureOffset)
    .put("temperatureSpan", temperatureSpan)
    .put("precipitationCap", precipitationCap)
    .put("temperatureRangeCap", temperatureRangeCap)
    .put("elevationCap", elevationCap)
    .put("slopeCap", slopeCap)
}

/**
 * Biome classification by weighted distance to a set of prototypes.
 *
 * Not a Whittaker lookup table. A table partitions parameter space into rectangles, and rectangles in
 * parameter space become visible rectangles *on the map* - straight-edged biome boundaries running along
 * lines of constant rainfall, which is a thing no landscape does. Prototypes with a weighted distance
 * produce boundaries that follow the shape of the climate instead, and the runner-up's score gives a
 * blend weight for free, so transitions can be gradients rather than steps.
 */
object Biomes {

  /** The axis scales [describe] normalises with. */
  val AXES = BiomeAxisRanges()

  /**
   * Normalised feature vector for one cell.
   *
   * Every axis is mapped into `[0,1]` so that the weights mean what they say. Without normalisation an
   * elevation axis in metres would dominate a seasonality axis in fractions by three orders of magnitude
   * regardless of any weight either was given.
   */
  fun describe(
    out: DoubleArray,
    temperature: Double,
    precipitation: Double,
    seasonality: Double,
    temperatureRange: Double,
    elevationAboveSea: Double,
    slope: Double,
    wetness: Double
  ) {
    out[BiomeAxis.TEMPERATURE] =
      ((temperature + AXES.temperatureOffset) / AXES.temperatureSpan).coerceIn(0.0, 1.0)
    // Square root scaled - see BiomeAxisRanges.precipitationCap for why.
    out[BiomeAxis.PRECIPITATION] = sqrt((precipitation / AXES.precipitationCap).coerceIn(0.0, 1.0))
    out[BiomeAxis.SEASONALITY] = seasonality.coerceIn(0.0, 1.0)
    out[BiomeAxis.TEMPERATURE_RANGE] = (temperatureRange / AXES.temperatureRangeCap).coerceIn(0.0, 1.0)
    out[BiomeAxis.ELEVATION] = (elevationAboveSea / AXES.elevationCap).coerceIn(0.0, 1.0)
    out[BiomeAxis.SLOPE] = (slope / AXES.slopeCap).coerceIn(0.0, 1.0)
    out[BiomeAxis.WETNESS] = wetness.coerceIn(0.0, 1.0)
  }

  /**
   * Nearest prototype, how much better it was than the runner-up, and which prototype that was.
   *
   * Confidence near zero means two biomes scored alike, which is exactly where a transition belongs -
   * a consumer that wants soft boundaries dithers or blends on this value rather than needing a second
   * classification pass.
   *
   * The runner-up's *identity* used to be computed and dropped: the loop tracked `secondScore` to derive the
   * confidence and never asked which prototype it belonged to, so a consumer could tell that a cell was in a
   * transition but not what it was a transition *between*. That is the second half of the architecture
   * document's deviation 4, and the KDoc above this object has always claimed the blend weight came "for free"
   * - it did, and the thing it was a weight *for* was missing.
   */
  fun classify(sample: DoubleArray, prototypes: List<BiomePrototype> = CLIMATIC): BiomeMatch {
    require(prototypes.isNotEmpty()) { "Cannot classify against an empty prototype set" }

    var best: BiomePrototype? = null
    var bestScore = Double.MAX_VALUE
    var secondBest: BiomePrototype? = null
    var secondScore = Double.MAX_VALUE

    for (prototype in prototypes) {
      var sum = 0.0
      for (axis in 0 until BiomeAxis.COUNT) {
        val delta = sample[axis] - prototype.at[axis]
        sum += prototype.weight[axis] * delta * delta
      }

      if (sum < bestScore) {
        // The demoted winner becomes the runner-up, and this has to happen *here* rather than only in the
        // branch below. That branch fires for prototypes that never led, so tracking the identity there alone
        // would miss every cell where the winner *changed* - which is most of them, since the first prototype
        // in the list leads until something beats it. Null on the first iteration, correctly: nothing has been
        // demoted yet.
        secondBest = best
        secondScore = bestScore
        best = prototype
        bestScore = sum
      } else if (sum < secondScore) {
        secondBest = prototype
        secondScore = sum
      }
    }

    val confidence = if (secondScore <= 0.0 || secondScore == Double.MAX_VALUE) {
      1.0
    } else {
      (1.0 - sqrt(bestScore / secondScore)).coerceIn(0.0, 1.0)
    }

    return BiomeMatch(best!!.biome, confidence, secondBest?.biome)
  }

  /**
   * The climatic biomes.
   *
   * Prototype positions are the *centre* of each biome's range rather than its edge, which is what a
   * nearest-prototype classifier wants - the boundary between two biomes lands halfway between their
   * centres automatically, and there is no table of thresholds to keep consistent.
   *
   * This list is the clearest candidate in the pipeline for moving into a data file. It is pure data, a
   * designer will want to tune it constantly, and nothing in it needs to be code.
   */
  val CLIMATIC: List<BiomePrototype> = listOf(
    //          biome                            temp  precip  seas  range  elev  slope  wet
    prototype(Biome.ICE_SHEET, 0.03, 0.15, 0.20, 0.60, 0.10, 0.10, 0.30, temperature = 2.4),
    prototype(Biome.TUNDRA, 0.20, 0.25, 0.30, 0.70, 0.10, 0.15, 0.50, temperature = 1.8),
    prototype(Biome.TAIGA, 0.32, 0.44, 0.30, 0.75, 0.15, 0.20, 0.60),
    prototype(Biome.COLD_DESERT, 0.28, 0.10, 0.40, 0.80, 0.30, 0.20, 0.14),
    prototype(Biome.TEMPERATE_FOREST, 0.52, 0.56, 0.25, 0.50, 0.12, 0.20, 0.65),
    prototype(Biome.TEMPERATE_RAINFOREST, 0.50, 0.82, 0.15, 0.35, 0.12, 0.25, 0.85),
    prototype(Biome.GRASSLAND, 0.52, 0.36, 0.40, 0.60, 0.12, 0.12, 0.45),
    prototype(Biome.STEPPE, 0.48, 0.24, 0.50, 0.72, 0.20, 0.12, 0.28),
    prototype(Biome.SHRUBLAND, 0.60, 0.29, 0.60, 0.45, 0.15, 0.25, 0.30),
    prototype(Biome.DESERT, 0.70, 0.06, 0.50, 0.55, 0.12, 0.15, 0.07, precipitation = 2.6),
    prototype(Biome.SAVANNA, 0.76, 0.39, 0.75, 0.30, 0.10, 0.12, 0.40, seasonality = 1.6),
    prototype(Biome.TROPICAL_SEASONAL_FOREST, 0.78, 0.60, 0.58, 0.22, 0.10, 0.20, 0.65),
    prototype(Biome.TROPICAL_RAINFOREST, 0.84, 0.92, 0.14, 0.12, 0.08, 0.20, 0.90),
    // Alpine wins on where it is rather than on what the weather is doing, so its elevation and slope
    // axes are weighted an order of magnitude above everything else's.
    prototype(
      Biome.ALPINE, 0.26, 0.46, 0.30, 0.60, 0.66, 0.58, 0.45,
      elevation = 4.5, slope = 2.6
    )
  )

  /**
   * Fingerprint of everything the classifier decides with: the prototype table and the axis scales.
   *
   * This is the largest single body of tuning in the pipeline - fourteen prototypes over seven axes, positions
   * and weights, one hundred and ninety-six numbers - and seven stages read the biome it produces. Moving one
   * of them moves every boundary on the map, so it belongs in the biome stage's version whether or not anybody
   * has got round to putting the table in a file.
   *
   * Folded by biome **name**, with the list index alongside. The name is what identifies a prototype; the index
   * is folded because a nearest-prototype tie is broken by position in this list, so reordering it can change
   * a classification even with every number unchanged.
   *
   * The two per-biome scalars are folded as well, over **every** entry rather than only the fourteen with a
   * prototype. They are tuning in exactly the same sense the prototype table is - `litter` is the largest term
   * in soil fertility and `canopy` decides where every tree in the world stands - and until this they reached
   * no version number at all, so retuning one would have left both the biome stage's cache and the vegetation
   * stage's looking valid.
   *
   * Note what is deliberately *not* here: [Biome]'s own ordinals. Those are written into the `BIOME` raster and
   * into chunk cache keys, so they are a stored data format rather than a tunable, and their guarantee is
   * append-only rather than fingerprinted.
   */
  fun catalogueDigest(): Long {
    val digest = ParamsDigest().nested("axes", AXES.digest().value)
    CLIMATIC.forEachIndexed { index, prototype ->
      digest.nested(
        "$index:${prototype.biome.name}",
        ParamsDigest()
          .put("at", prototype.at.toList())
          .put("weight", prototype.weight.toList())
          .value
      )
    }
    for (biome in Biome.entries) {
      digest.nested(
        "biome:${biome.name}",
        ParamsDigest().put("litter", biome.litter).put("canopy", biome.canopy).value
      )
    }
    return digest.value
  }

  private fun prototype(
    biome: Biome,
    temperatureAt: Double,
    precipitationAt: Double,
    seasonalityAt: Double,
    rangeAt: Double,
    elevationAt: Double,
    slopeAt: Double,
    wetnessAt: Double,
    temperature: Double = 1.6,
    precipitation: Double = 1.5,
    seasonality: Double = 0.8,
    range: Double = 0.55,
    elevation: Double = 0.45,
    slope: Double = 0.5,
    wetness: Double = 0.9
  ): BiomePrototype {
    val at = DoubleArray(BiomeAxis.COUNT)
    at[BiomeAxis.TEMPERATURE] = temperatureAt
    at[BiomeAxis.PRECIPITATION] = precipitationAt
    at[BiomeAxis.SEASONALITY] = seasonalityAt
    at[BiomeAxis.TEMPERATURE_RANGE] = rangeAt
    at[BiomeAxis.ELEVATION] = elevationAt
    at[BiomeAxis.SLOPE] = slopeAt
    at[BiomeAxis.WETNESS] = wetnessAt

    val weight = DoubleArray(BiomeAxis.COUNT)
    weight[BiomeAxis.TEMPERATURE] = temperature
    weight[BiomeAxis.PRECIPITATION] = precipitation
    weight[BiomeAxis.SEASONALITY] = seasonality
    weight[BiomeAxis.TEMPERATURE_RANGE] = range
    weight[BiomeAxis.ELEVATION] = elevation
    weight[BiomeAxis.SLOPE] = slope
    weight[BiomeAxis.WETNESS] = wetness

    return BiomePrototype(biome, at, weight)
  }
}
