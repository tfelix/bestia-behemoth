package net.bestia.worldgen.place

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ParamsDigest

/**
 * What kind of place a region is, and the word its name ends in.
 *
 * The form is lowercase because it feeds two different renderings: `Names.region` capitalises it for
 * "Elm Downs", and `Names.site`'s `else` branch takes it verbatim for "the downs of Ashford". Storing
 * it capitalised would mean one of the two carrying a `lowercase()` call, and the site path is the one
 * that already works for every other form in that file.
 */
enum class RegionKind(val form: String) {

  VALE("vale"),
  DOWNS("downs"),
  FELLS("fells"),
  MARCH("march"),
  WASTE("waste"),
  MOOR("moor"),
  FOREST("wood"),
  COAST("coast"),

  /** A stretch of enclosed or shelf water. */
  SOUND("sound"),

  /** Open water deep enough that nobody has a reason to be there. */
  DEEP("deep");

  companion object {

    /**
     * What a region of this shape would be called.
     *
     * Ordered as a cascade rather than a score because the strongest signal should win outright: a
     * mountain region is fells whatever grows on it, and a region half under water is a coast whatever
     * the dry half is. A weighted blend of the two produced "the Elm Fells" for a wooded hillside,
     * which is a name for one thing or the other, not both.
     */
    fun of(
      dominantBiome: Biome,
      relief: Double,
      landShare: Double,
      coastalShare: Double,
      meanElevation: Double
    ): RegionKind {
      if (landShare < WATER_REGION_LAND_SHARE) {
        return if (meanElevation < DEEP_WATER_ELEVATION) DEEP else SOUND
      }

      if (relief > FELLS_RELIEF) return FELLS
      if (dominantBiome == Biome.BEACH || coastalShare > COASTAL_SHARE) return COAST
      if (dominantBiome in WASTES) return WASTE
      if (dominantBiome in WETLANDS) return MOOR
      if (dominantBiome in WOODLANDS) return FOREST
      if (dominantBiome == Biome.GRASSLAND) return DOWNS
      if (relief > VALE_RELIEF) return VALE

      return MARCH
    }

    /**
     * Fingerprint of the form words.
     *
     * `history/Names.kt`'s own argument, applied here: a name is a seed plus a word list, so changing
     * the list renames the world and a renamed world looks like a working world. Region names are not
     * stored anywhere today, which is why this does not reach `pipelineVersion` - but it will matter
     * the moment anything caches one or the words reach the wire.
     */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      for (kind in entries) digest.put(kind.name, kind.form)
      return digest.value
    }

    /** Below this share of dry land a region is water, and takes a water name. */
    private const val WATER_REGION_LAND_SHARE = 0.5

    /**
     * Share of a region's cells that must touch water for it to be named for its shore.
     *
     * Measured against the region's cell count, not its perimeter: a seven-kilometre region is about
     * fifty cells with a thirty-cell edge, so a quarter of the cells touching water means most of the
     * edge is coastline rather than a neighbour.
     *
     * This replaced a test on the land share, which was dead code. `RegionGrowth` refuses to cross a
     * coastline, so every land region comes back at a land share of one and the branch never fired -
     * exactly one region in a hundred was named a coast, and only because beach happened to be its
     * dominant biome.
     */
    private const val COASTAL_SHARE = 0.25

    private const val DEEP_WATER_ELEVATION = -200.0

    /**
     * p95 minus p5 elevation, in metres, at which a region reads as mountains rather than hills.
     *
     * Set off the measured distribution rather than from intuition - see `RegionCalibrationTest`. On the
     * genesis world land regions run p10 306 m, p50 617 m, p90 1337 m, so the first guess of 700 m made
     * mountains of nearly two regions in five. This takes roughly the top eighth, and [VALE_RELIEF] sits
     * at the median so "hilly" means more than half.
     */
    private const val FELLS_RELIEF = 1_200.0

    private const val VALE_RELIEF = 600.0

    private val WASTES = setOf(Biome.DESERT, Biome.BADLANDS, Biome.COLD_DESERT, Biome.DRYLAND)

    private val WETLANDS = setOf(Biome.BOG, Biome.SWAMP)

    private val WOODLANDS = setOf(
      Biome.TAIGA, Biome.TEMPERATE_FOREST, Biome.TEMPERATE_RAINFOREST,
      Biome.TROPICAL_SEASONAL_FOREST, Biome.TROPICAL_RAINFOREST, Biome.RIPARIAN
    )
  }
}
