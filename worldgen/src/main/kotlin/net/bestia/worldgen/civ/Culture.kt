package net.bestia.worldgen.civ

/**
 * What a culture values in a place to live.
 *
 * Per-culture weights are the cheapest way to get civilisational variety, by a wide margin. The same
 * habitability terms scored differently put a seafaring people on every sheltered inlet and a steppe people
 * out on the grazing land, with no new terms, no new stages, and no scripting - and the two produce visibly
 * different maps of the same world.
 *
 * Weights are relative; the field is normalised, so only their ratios matter.
 */
data class Culture(
  val name: String,
  val freshWater: Double = 1.0,
  val soilFertility: Double = 1.0,
  val arableSlope: Double = 0.8,
  val defensibility: Double = 0.6,
  val resources: Double = 0.7,
  val climate: Double = 0.9,
  val harbour: Double = 0.5,
  val grazing: Double = 0.3,
  /** Subtracted, not added: floodplain, volcanic ground, avalanche track. */
  val hazardAversion: Double = 0.8,
  /** Minimum separation between cities of this culture, in metres. */
  val citySeparation: Double = 55_000.0
) {

  companion object {

    /** Mixed farming in river valleys. The default, and what most of a temperate world looks like. */
    val AGRARIAN = Culture(
      name = "agrarian",
      freshWater = 1.3,
      soilFertility = 1.4,
      arableSlope = 1.0,
      defensibility = 0.5,
      resources = 0.6,
      climate = 1.0,
      harbour = 0.3,
      grazing = 0.2,
      hazardAversion = 0.7
    )

    /**
     * Coast dwellers. Heavily weight sheltered water and barely care about soil.
     *
     * Fjord country scores outstandingly for this culture - deep sheltered water with steep sides - which is
     * exactly why real fjord regions are densely settled at the waterline despite terrible agriculture.
     */
    val SEAFARING = Culture(
      name = "seafaring",
      freshWater = 0.9,
      soilFertility = 0.4,
      arableSlope = 0.4,
      defensibility = 0.7,
      resources = 0.8,
      climate = 0.7,
      harbour = 2.0,
      grazing = 0.1,
      hazardAversion = 0.5,
      citySeparation = 70_000.0
    )

    /** Herders. Want open grass and water, discount arable soil, and spread out. */
    val PASTORAL = Culture(
      name = "pastoral",
      freshWater = 1.2,
      soilFertility = 0.3,
      arableSlope = 0.5,
      defensibility = 0.4,
      resources = 0.5,
      climate = 0.8,
      harbour = 0.1,
      grazing = 1.8,
      hazardAversion = 0.4,
      citySeparation = 90_000.0
    )

    /** Miners and metalworkers. Follow the ore into ground nobody else wants. */
    val HIGHLAND = Culture(
      name = "highland",
      freshWater = 1.0,
      soilFertility = 0.5,
      arableSlope = 0.3,
      defensibility = 1.2,
      resources = 2.0,
      climate = 0.5,
      harbour = 0.2,
      grazing = 0.6,
      hazardAversion = 0.3,
      citySeparation = 45_000.0
    )

    val ALL = listOf(AGRARIAN, SEAFARING, PASTORAL, HIGHLAND)
  }
}

/** Settlement sizes, largest first. Placement runs tier by tier down this list. */
enum class SettlementTier(
  val label: String,
  /** Minimum separation from another settlement of the same tier, in metres. */
  val separation: Double,
  val minPopulation: Int,
  val maxPopulation: Int
) {
  CITY("city", 55_000.0, 6_000, 40_000),
  TOWN("town", 22_000.0, 1_200, 6_000),
  VILLAGE("village", 8_000.0, 180, 1_200),
  HAMLET("hamlet", 3_500.0, 20, 180);

  /** Radius of the built area, roughly, in metres. Used for terrain grading. */
  val footprintRadius: Double
    get() = when (this) {
      CITY -> 900.0
      TOWN -> 420.0
      VILLAGE -> 190.0
      HAMLET -> 90.0
    }
}

/** Station channel names on a [net.bestia.worldgen.vector.FeatureKind.SETTLEMENT] marker. */
object SettlementChannels {
  /** [SettlementTier] ordinal. A category; read with `toInt()`, never interpolated. */
  const val TIER = "tier"

  /** Index into [Culture.ALL]. */
  const val CULTURE = "culture"

  const val POPULATION = "population"

  /** The habitability score that got it placed, 0 to 1. */
  const val HABITABILITY = "habitability"

  /** Ground elevation at the site, in metres. */
  const val ELEVATION = "elevation"
}
