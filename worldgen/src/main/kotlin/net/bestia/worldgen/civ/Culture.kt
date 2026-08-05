package net.bestia.worldgen.civ

import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest

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
  val citySeparation: Double = 55_000.0,

  // --- What its towns look like (step 8) ------------------------------------------------------------

  val layout: TownLayout = TownLayout.ORGANIC,

  /**
   * Share of buildings walled in stone rather than timber, before wealth is taken into account.
   *
   * The single cheapest way to make two cultures' towns distinguishable from a hundred metres away,
   * which is the distance most of them will ever be seen from.
   */
  val stoneShare: Double = 0.35,

  /** Mean storeys of an ordinary dwelling. Fractional: the remainder is the chance of one more. */
  val storeys: Double = 1.4,

  // --- What its people do (step 9) ------------------------------------------------------------------

  /** Sector biases, relative. Normalised at use, so only the ratios matter. */
  val craftBias: Double = 1.0,
  val tradeBias: Double = 1.0,
  val serviceBias: Double = 1.0,
  val adminBias: Double = 1.0,
  val clergyBias: Double = 1.0,
  val militaryBias: Double = 1.0,

  /**
   * How much of the population farms, at zero technology.
   *
   * A pre-industrial society is mostly farmers, and how *few* it can get away with is the thing that
   * decides whether it has towns at all - so this is the number the whole economy hangs off.
   */
  val farmShare: Double = 0.78,

  // --- How it behaves (step 10) ---------------------------------------------------------------------

  /** Appetite for war, 0 to 1. Scales how fast hostility accumulates with a neighbour. */
  val bellicosity: Double = 0.4,

  /** Rate of technological advance, relative. Raises carrying capacity over the simulated span. */
  val inventiveness: Double = 1.0,

  /** How far it will expand from its capital, in metres. Seafarers go a long way; farmers do not. */
  val expansionRange: Double = 90_000.0
) : Params {

  override fun digest() = ParamsDigest()
    .put("name", name)
    .put("freshWater", freshWater)
    .put("soilFertility", soilFertility)
    .put("arableSlope", arableSlope)
    .put("defensibility", defensibility)
    .put("resources", resources)
    .put("climate", climate)
    .put("harbour", harbour)
    .put("grazing", grazing)
    .put("hazardAversion", hazardAversion)
    .put("citySeparation", citySeparation)
    .put("layout", layout)
    .put("stoneShare", stoneShare)
    .put("storeys", storeys)
    .put("craftBias", craftBias)
    .put("tradeBias", tradeBias)
    .put("serviceBias", serviceBias)
    .put("adminBias", adminBias)
    .put("clergyBias", clergyBias)
    .put("militaryBias", militaryBias)
    .put("farmShare", farmShare)
    .put("bellicosity", bellicosity)
    .put("inventiveness", inventiveness)
    .put("expansionRange", expansionRange)

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
      hazardAversion = 0.7,
      layout = TownLayout.ORGANIC,
      stoneShare = 0.25,
      storeys = 1.4,
      craftBias = 1.0,
      tradeBias = 0.9,
      clergyBias = 1.3,
      militaryBias = 0.7,
      farmShare = 0.80,
      bellicosity = 0.35,
      expansionRange = 70_000.0
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
      citySeparation = 70_000.0,
      // A planned grid, because a trading port is laid out by whoever chartered it rather than grown from
      // a crossroads - and the contrast with the agrarian tangle next door is the point.
      layout = TownLayout.GRID,
      stoneShare = 0.45,
      storeys = 2.1,
      craftBias = 1.1,
      tradeBias = 2.2,
      serviceBias = 1.4,
      adminBias = 1.2,
      clergyBias = 0.8,
      farmShare = 0.62,
      bellicosity = 0.45,
      inventiveness = 1.3,
      expansionRange = 200_000.0
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
      citySeparation = 90_000.0,
      layout = TownLayout.ORGANIC,
      stoneShare = 0.10,
      storeys = 1.1,
      craftBias = 0.7,
      tradeBias = 1.2,
      serviceBias = 0.8,
      adminBias = 0.6,
      clergyBias = 0.7,
      militaryBias = 1.8,
      farmShare = 0.84,
      bellicosity = 0.75,
      inventiveness = 0.8,
      expansionRange = 160_000.0
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
      citySeparation = 45_000.0,
      layout = TownLayout.ORGANIC,
      stoneShare = 0.80,
      storeys = 1.7,
      craftBias = 2.0,
      tradeBias = 1.0,
      serviceBias = 0.9,
      adminBias = 0.8,
      clergyBias = 0.9,
      militaryBias = 1.3,
      farmShare = 0.70,
      bellicosity = 0.5,
      inventiveness = 1.2,
      expansionRange = 55_000.0
    )

    val ALL = listOf(AGRARIAN, SEAFARING, PASTORAL, HIGHLAND)

    fun byIndex(index: Int): Culture = ALL[index.coerceIn(0, ALL.size - 1)]

    fun indexOf(culture: Culture): Int = ALL.indexOf(culture).coerceAtLeast(0)

    /**
     * Fingerprint of the whole roster, for the five stages that read cultures.
     *
     * Folded **in list order, with the index**, which is the part that matters. [indexOf] is written into a
     * station channel on every settlement and into `CivRecord.cultureIndex`, so the position of a culture in
     * [ALL] is a stored data format rather than a presentation detail: reordering this list silently
     * reassigns every settlement in an existing world to a different people. Folding the index makes that a
     * version change instead.
     *
     * A retune of one culture's weights is the ordinary case and moves the digest through that culture's own
     * [digest].
     */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      ALL.forEachIndexed { index, culture -> digest.nested("$index:${culture.name}", culture.digest().value) }
      return digest.value
    }
  }
}

/**
 * How a culture lays out a town.
 *
 * Two, not a spectrum, because they are produced by genuinely different algorithms rather than by the
 * same one with a parameter: one grows outwards from the gates and snaps to what it meets, the other is
 * surveyed once and clipped to the ground. A blend of the two is not a third kind of town, it is a bug.
 */
enum class TownLayout { ORGANIC, GRID }

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

  /**
   * Radius of the built area, roughly, in metres. Used for terrain grading.
   *
   * ### Why these grew by half
   *
   * They were 900 / 420 / 190 / 90, and those numbers were the radius of a settlement that was literally a disc.
   * A town is now a shape - see `TownBoundary` - and a shape needs a wider bounding circle than a disc of the
   * same area does, by `StreetParams.boundaryReachFactor`. Raising these by that factor is what makes
   * de-circularising a settlement change its outline rather than shrink it: at the old values every town in the
   * world came out about forty per cent short of the plots its own population asked for.
   *
   * It is worth knowing that **this number, not population, is what has always decided how big a settlement is**.
   * `TownStage.builtRadiusFor` takes the smaller of the population's own radius and what fits inside this, and
   * the cap binds at every tier - a forty-thousand city asks for 1.5 km and got 833 m. So these values were
   * already holding settlements below their populations, and this change moves them closer to it rather than
   * further away, which is the second reason to make it.
   *
   * The cost is real and is terrain: the grading feature is a disc of this radius, so more ground is levelled
   * under each settlement than the town standing on it needs. Fixing *that* means giving the grading the town's
   * own outline, which cannot happen here - `SettlementStage` emits the grading and runs before history, so it
   * does not yet know the population the outline is sized from.
   */
  val footprintRadius: Double
    get() = when (this) {
      CITY -> 1_310.0
      TOWN -> 610.0
      VILLAGE -> 280.0
      HAMLET -> 130.0
    }

  companion object {

    /**
     * Fingerprint of the tier table, folded in declaration order.
     *
     * These four numbers are tunables in everything but their storage: the separation decides how many
     * settlements a world holds, the population range decides what each one is, and [footprintRadius] decides
     * how much ground gets graded flat under it. That the table is an enum rather than a params class is a
     * choice about where the values live, not about whether changing one changes the world.
     *
     * [footprintRadius] is included even though it is a computed `when` rather than a constructor field -
     * exactly because it is: a number that reaches terrain from inside a getter is the easiest kind to change
     * without noticing that anything downstream cared.
     */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      for (tier in entries) {
        digest.nested(
          "${tier.ordinal}:${tier.name}",
          ParamsDigest()
            .put("label", tier.label)
            .put("separation", tier.separation)
            .put("minPopulation", tier.minPopulation)
            .put("maxPopulation", tier.maxPopulation)
            .put("footprintRadius", tier.footprintRadius)
            .value
        )
      }
      return digest.value
    }
  }
}

/** Station channel names on a [net.bestia.worldgen.vector.FeatureKind.SETTLEMENT] marker. */
object SettlementChannels {

  /**
   * Dense index of this settlement within the placement stage's output, from zero.
   *
   * The join key for everything downstream: history, town layout and the economy all key their own
   * markers on it. A small integer rather than the marker's [net.bestia.worldgen.vector.FeatureId]
   * because a station channel is a `Double` and a feature id is a 64-bit hash - storing one would
   * silently lose its low eleven bits, and two settlements whose ids differ only there would become the
   * same place.
   */
  const val INDEX = "index"

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
