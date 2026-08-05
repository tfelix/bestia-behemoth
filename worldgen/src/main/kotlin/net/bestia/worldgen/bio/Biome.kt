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
   * Sparse values are the interesting ones. Dryland at 0.11 is what makes it scrub rather than either
   * grassland or forest, and grassland at 0.05 is the lone tree in the middle of a field that gives a
   * landscape its scale. It is also the axis the [BOG] / [SWAMP] split is *visible* on: 0.06 against 0.50 is
   * open moss against standing forest, out of one rung in `BiomeStage.override`.
   */
  val canopy: Double
) {

  //     biome                                       litter  canopy
  // Water.
  OCEAN("ocean", 0.0, 0.0),
  LAKE("lake", 0.0, 0.0),

  // Cold.
  /**
   * Permanent ice, at any elevation.
   *
   * There was a separate `GLACIER` beside this, distinguished only by an elevation test in
   * `BiomeStage.override` and behaving identically in every downstream table - the same hostility, the same
   * ice cap, the same refusal of caves, trees and crystals, the same movement cost. Two names for one
   * behaviour is a table every consumer has to keep consistent for no gain, so there is one. If high ice
   * ever needs to differ from low ice, the difference is elevation, which every reader already has.
   */
  ICE_SHEET("ice sheet", 0.0, 0.0),
  TUNDRA("tundra", 0.25, 0.02),
  TAIGA("taiga", 0.45, 0.70),
  COLD_DESERT("cold desert", 0.05, 0.0),
  // Krummholz: a few stunted things at the tree line and nothing above it.
  ALPINE("alpine", 0.2, 0.04),

  // Temperate.
  TEMPERATE_FOREST("temperate forest", 0.85, 0.80),
  TEMPERATE_RAINFOREST("temperate rainforest", 0.9, 0.85),
  GRASSLAND("grassland", 0.7, 0.05),

  /**
   * Semi-arid open country with scattered woody plants: the one biome between grassland and desert.
   *
   * There were three of these - `STEPPE`, `SHRUBLAND` and `SAVANNA` - and they were one place under three
   * names. Every downstream table gave two of them the same answer and the third a number within noise of it:
   * identical soil, identical cap, identical bare cover, identical movement cost, identical furs, and pasture
   * and grazing that differed by 0.3 on a term already multiplied by slope. At the voxel tier all three came
   * out as `GRASS` over `DIRT` with a slightly different tree count, so a player standing in one could not
   * tell which. Measured land shares were 0.6%, 21.2% and 1.4% on the reference world, so the two that carried
   * the extra names were also the two that barely existed.
   *
   * The merge is a *rename of the one that won* plus two deletions, which is why the map barely moves: the
   * prototype sits within 0.03 of where `SHRUBLAND`'s did on every axis, widened on seasonality to take in the
   * ground savanna held. What used to distinguish them - how many trees stand on it - survives where it always
   * lived, in [canopy], and now varies continuously with the climate instead of stepping between three values.
   */
  DRYLAND("dryland", 0.33, 0.11),

  // Warm.
  DESERT("desert", 0.02, 0.0),
  TROPICAL_SEASONAL_FOREST("tropical seasonal forest", 0.75, 0.65),
  TROPICAL_RAINFOREST("tropical rainforest", 0.6, 0.90),

  // Edge biomes: driven by adjacency to something rather than by climate.

  /*
   * There was a single `WETLAND` here, and it covered two places that have nothing in common but standing water.
   *
   * A boreal mire and a tropical swamp are opposite ecosystems: one is cold, acidic and anoxic, so it *preserves*
   * and accumulates peat, and the other is warm and biologically frantic, so it rots and breeds. Collapsing them
   * gave both the same peat floor, the same sparse canopy and the same hostility, which is the plausible-wrong
   * answer this file's `Biome.entries` comment warns about - it looked fine on a map and was wrong everywhere a
   * consumer asked what grows here or what can be gathered.
   *
   * They are split on temperature at the wetland rung of `BiomeStage.override`, which is one extra condition
   * because the rung already has the temperature in scope. There is deliberately no third, temperate `MARSH`
   * between them: a reed fen is the arithmetic mean of these two and owns nothing either of them does not, which
   * is the same test the three dry-grass biomes above failed.
   */

  /**
   * Cold peatland: open, mossy, and the reason anything organic survives here.
   *
   * Almost no canopy, because a raised bog kills the trees that try - a few stunted spruce at the margin is the
   * whole of it, and that sparseness is what makes it read as bog rather than as wet taiga. The high [litter] is
   * not productivity but the *absence of decay*: peat is dead matter that never broke down, which is precisely
   * why `ResourceStage`'s bog-iron route and everything preserved in one are found here.
   */
  BOG("bog", 0.85, 0.06),

  /**
   * Warm forested wetland: closed canopy standing in water.
   *
   * The [canopy] is the load-bearing difference from [BOG] and it is deliberately high - a swamp is a *wooded*
   * wetland, and cypress or mangrove over black water is a different silhouette from open moss. It feeds
   * `VegetationScatter` directly, so the two biomes come out of the chunk tier looking as unalike as they are.
   */
  SWAMP("swamp", 0.75, 0.50),
  // Gallery forest: the band of trees along a watercourse in country that has none away from it.
  RIPARIAN("riparian", 0.8, 0.60),
  BEACH("beach", 0.05, 0.0),
  BADLANDS("badlands", 0.05, 0.0),

  /*
   * Geological biomes: driven by what the crust is doing rather than by adjacency or by the weather.
   *
   * Neither has a prototype in [Biomes.CLIMATIC], for the same reason BEACH, WETLAND and BADLANDS have none:
   * the classifier's seven axes are climate, elevation, slope and wetness, and **none of them can see a vent**.
   * A prototype would be a claim that volcanic ground has a characteristic climate, which is exactly backwards -
   * there are volcanoes under ice caps and volcanoes in deserts, and that is the interesting thing about them.
   */

  /**
   * Cooling rock and open lava: the cone itself, and the flow fields around it.
   *
   * Zero litter and zero canopy, and unlike the deleted `CLIFF` those are the truth about the place rather than
   * a side effect of how it was assigned. Fresh basalt carries no soil, which is why [BiomeStage.fertilityAt]
   * returns early for it: the formula's floor would otherwise give it about 0.4 and pull a settlement onto it.
   */
  VOLCANIC_FIELD("volcanic field", 0.0, 0.0),

  /**
   * Vents, hot springs and sulfur crusts: the valley floors around a volcano rather than the cone.
   *
   * Stays on the ordinary fertility formula, deliberately. A town in a geothermal basin is Reykjavik, and there
   * is nothing wrong with one - the ground is warm, the water is hot, and the surrounding country is habitable.
   * The sparse canopy is scrub around the vents rather than nothing at all.
   */
  GEOTHERMAL_BASIN("geothermal basin", 0.15, 0.02);

  /*
   * There was a `CLIFF` here, and it was not a biome.
   *
   * It carried zero litter and zero canopy, capped everything in one grey gravel whatever country it stood
   * in, and was assigned by a single slope threshold - which is to say it was a *slope flag* wearing an
   * ecosystem's clothes, and it cost every downstream `when` an arm to say "steep" in a vocabulary about
   * what grows. Worse, it erased the biome underneath: an ice cliff, a desert scarp and a granite crag were
   * indistinguishable once classified, so no consumer could tell them apart even though all three look
   * completely different.
   *
   * Steep ground now keeps its climatic biome, and steepness is read where it is needed. The world tier
   * takes it from `LayerId.ELEVATION` through `Grid.gradient`, which every consumer already had in scope -
   * so there is deliberately no `LayerId.STEEPNESS`, for `BIOME_SECONDARY`'s reason: a second raster that
   * is a function of one already on disk is a raster that can disagree with itself. The voxel tier takes a
   * central difference over the *materialised* surface, which is strictly better than either, because the
   * raster knows about mountain fronts and knows nothing about the fjord wall a vector feature cut.
   *
   * See `SurfaceCover.bareCover`, which is where the per-biome bare rock the old single GRAVEL replaced
   * now lives.
   */

  val isWater get() = this == OCEAN || this == LAKE

  /*
   * There is deliberately no `of(ordinal)` here.
   *
   * There was, and it was `entries[ordinal.coerceIn(0, entries.size - 1)]`. Coercion is the wrong reflex for
   * a value read out of a layer: `LayerId.BIOME_SECONDARY` stores `NO_SECONDARY = -1` for a cell with no
   * runner-up, and that read back as `OCEAN` - a silent wrong answer, in the one place where the reader is
   * asking precisely because it does not know. `Layer.kt` warned about it in prose and callers were told to
   * use `entries.getOrNull` instead, which is a rule somebody has to remember. Deleting the function is the
   * version of that rule the compiler enforces.
   *
   * Read a `BIOME` ordinal with `Biome.entries[v]`, which throws on a value that is not one. Read anything
   * that might carry a sentinel with `Biome.entries.getOrNull(v)` and handle the null.
   */
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
  /**
   * Ground gradient mapped onto the unit interval. Above this the classifier's slope axis saturates.
   *
   * Saturating means the classifier stops distinguishing steep from steeper, not that anything is
   * reclassified: what happens above this is that every prototype scores the slope axis identically, so the
   * decision falls to the other six. Steep ground therefore keeps whatever climate it has, and how bare it
   * looks is decided later and elsewhere - see `SurfaceCover.bareCover`.
   *
   * Do not retune casually. Every biome boundary in every world is a distance in this normalised space, so
   * moving it moves all of them.
   */
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
    /*
     * Cold desert, moved up to meet the re-sited DESERT below and carry the cool half of the world's arid land.
     *
     * Temperature was 0.28, which is -6.8 C, and that left the 0 to 13 C arid band with no home: too warm for
     * this and, before the fix below, too cool for anything else, so it went to grassland and shrubland.
     * Measured on the reference world, the land under 190 mm a year is spread almost evenly from -10 C to +40 C
     * with a third of it below +5, so both ends need a prototype. 0.38 is +0.7 C, which puts the midpoint
     * against DESERT at about +12 C.
     *
     * Wetness was 0.14 and is 0.26 for the reason spelled out under DESERT: [BiomeStage.wetnessAt] cannot
     * emit a value that low on flat ground, so the old figure was a handicap on the axis rather than a
     * description of the place. Cold desert won ground anyway, which is why the bug showed up here as
     * "not as much cold desert as there should be" rather than as an absence.
     */
    prototype(Biome.COLD_DESERT, 0.38, 0.10, 0.40, 0.80, 0.30, 0.20, 0.26),
    prototype(Biome.TEMPERATE_FOREST, 0.52, 0.56, 0.25, 0.50, 0.12, 0.20, 0.65),
    prototype(Biome.TEMPERATE_RAINFOREST, 0.50, 0.82, 0.15, 0.35, 0.12, 0.25, 0.85),
    prototype(Biome.GRASSLAND, 0.52, 0.36, 0.40, 0.60, 0.12, 0.12, 0.45),
    // The three merged into one; see [Biome.DRYLAND]. Within 0.03 of the old SHRUBLAND on every axis except
    // seasonality, which is widened from 0.60 to take in the strongly seasonal ground SAVANNA held. Savanna's
    // `seasonality = 1.6` boost is deliberately *not* carried over: it existed to keep savanna off steppe's
    // ground, and with one prototype there is nothing left to separate.
    prototype(Biome.DRYLAND, 0.60, 0.30, 0.62, 0.50, 0.15, 0.16, 0.32),
    /*
     * Desert, re-sited. The old position was `0.70, 0.06, ..., 0.07` and it never won a single cell on any world
     * measured - the reference world produced 8.9% of its land under 190 mm of rain a year and classified none of
     * it as desert, handing 34% to shrubland, 21% to grassland and 8% to *temperate forest*.
     *
     * Three separate things were wrong and all three are fixed here:
     *
     * 1. **Wetness 0.07 is unreachable on the terrain deserts occupy.** [BiomeStage.wetnessAt] is
     *    `0.5*rain + 0.28*shed + 0.22*collected`, and `shed` is 1 on flat ground - so a dead-level, zero-rain
     *    cell still scores about 0.29 and cannot approach 0.07. That value describes a dry *cliff*. Deserts are
     *    flat, so the axis with the second-largest weight was permanently against them. 0.26 sits just under
     *    what flat arid ground actually measures, which keeps a mild dryness preference without being a value
     *    the formula cannot emit.
     * 2. **Precipitation 0.06 back-solves to 14 mm a year**, which is the Atacama rather than a desert. At 0.15
     *    (90 mm) and with the existing 2.6 weight, the desert/dryland boundary lands where
     *    `2.6(x-0.15)^2 = 1.5(x-0.30)^2` - about 180 mm a year, which is the real arid/semi-arid line.
     * 3. **Temperature 0.70 was very nearly right and is now 0.76.** The first attempt at this fix moved it
     *    *down* to 0.64 on the theory that the world's arid land is a cool continental interior; measuring the
     *    arid land against the temperature raster showed that was wrong - it spreads almost evenly from -10 C
     *    to +40 C - and `LocalTemperatureTest` then caught what the wrong theory cost. A desert centred on
     *    +16.6 C came out **comfortable 98.8% of the year**, which is not a desert anybody needs to pack for.
     *    0.76 is +24.4 C, so this prototype takes the hot half of the arid land and COLD_DESERT above takes
     *    the cool half, which is the division those two names were always for.
     *
     * Precipitation stays the dominant axis, and that is the point: what makes a desert is that it does not
     * rain, not that it is hot. What decides *which* desert is the temperature.
     */
    prototype(Biome.DESERT, 0.76, 0.15, 0.50, 0.55, 0.12, 0.15, 0.26, precipitation = 2.6),
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
   * This is the largest single body of tuning in the pipeline - twelve prototypes over seven axes, positions
   * and weights, one hundred and sixty-eight numbers - and seven stages read the biome it produces. Moving one
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
