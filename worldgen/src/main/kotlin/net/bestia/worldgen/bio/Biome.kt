package net.bestia.worldgen.bio

import kotlin.math.sqrt

/**
 * The biome vocabulary.
 *
 * Ordinals are written into the [net.bestia.worldgen.core.LayerId.BIOME] raster and therefore into
 * chunk cache keys, so **entries may be appended but not reordered or removed** once a world has been
 * generated. That is the same constraint as any other on-disk enum, and it is worth stating here rather
 * than discovering it when an existing world's biomes all shift by one.
 */
enum class Biome(val label: String, val litter: Double) {

  // Water.
  OCEAN("ocean", 0.0),
  LAKE("lake", 0.0),

  // Cold.
  ICE_SHEET("ice sheet", 0.0),
  GLACIER("glacier", 0.0),
  TUNDRA("tundra", 0.25),
  TAIGA("taiga", 0.45),
  COLD_DESERT("cold desert", 0.05),
  ALPINE("alpine", 0.2),

  // Temperate.
  TEMPERATE_FOREST("temperate forest", 0.85),
  TEMPERATE_RAINFOREST("temperate rainforest", 0.9),
  GRASSLAND("grassland", 0.7),
  STEPPE("steppe", 0.35),
  SHRUBLAND("shrubland", 0.3),

  // Warm.
  DESERT("desert", 0.02),
  SAVANNA("savanna", 0.4),
  TROPICAL_SEASONAL_FOREST("tropical seasonal forest", 0.75),
  TROPICAL_RAINFOREST("tropical rainforest", 0.6),

  // Edge biomes: driven by adjacency to something rather than by climate.
  WETLAND("wetland", 0.8),
  RIPARIAN("riparian", 0.8),
  BEACH("beach", 0.05),
  BADLANDS("badlands", 0.05),
  CLIFF("cliff", 0.0);

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

/** The best match and how clearly it won. */
class BiomeMatch(val biome: Biome, val confidence: Double)

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
    out[BiomeAxis.TEMPERATURE] = ((temperature + 25.0) / 65.0).coerceIn(0.0, 1.0)
    // Square root scaled: the biome boundaries that matter are crowded into the dry end, where the
    // difference between 200 and 500 mm decides desert against grassland, while the difference between
    // 2500 and 2800 decides nothing.
    out[BiomeAxis.PRECIPITATION] = sqrt((precipitation / 4000.0).coerceIn(0.0, 1.0))
    out[BiomeAxis.SEASONALITY] = seasonality.coerceIn(0.0, 1.0)
    out[BiomeAxis.TEMPERATURE_RANGE] = (temperatureRange / 45.0).coerceIn(0.0, 1.0)
    out[BiomeAxis.ELEVATION] = (elevationAboveSea / 4000.0).coerceIn(0.0, 1.0)
    out[BiomeAxis.SLOPE] = (slope / 0.35).coerceIn(0.0, 1.0)
    out[BiomeAxis.WETNESS] = wetness.coerceIn(0.0, 1.0)
  }

  /**
   * Nearest prototype, plus how much better it was than the runner-up.
   *
   * Confidence near zero means two biomes scored alike, which is exactly where a transition belongs -
   * a consumer that wants soft boundaries dithers or blends on this value rather than needing a second
   * classification pass.
   */
  fun classify(sample: DoubleArray, prototypes: List<BiomePrototype> = CLIMATIC): BiomeMatch {
    var best = prototypes[0]
    var bestScore = Double.MAX_VALUE
    var secondScore = Double.MAX_VALUE

    for (prototype in prototypes) {
      var sum = 0.0
      for (axis in 0 until BiomeAxis.COUNT) {
        val delta = sample[axis] - prototype.at[axis]
        sum += prototype.weight[axis] * delta * delta
      }

      if (sum < bestScore) {
        secondScore = bestScore
        bestScore = sum
        best = prototype
      } else if (sum < secondScore) {
        secondScore = sum
      }
    }

    val confidence = if (secondScore <= 0.0 || secondScore == Double.MAX_VALUE) {
      1.0
    } else {
      (1.0 - sqrt(bestScore / secondScore)).coerceIn(0.0, 1.0)
    }

    return BiomeMatch(best.biome, confidence)
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
