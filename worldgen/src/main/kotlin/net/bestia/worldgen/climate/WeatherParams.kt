package net.bestia.worldgen.climate

import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText

/**
 * Tuning for the weather model.
 *
 * ### Sizing is in fractions of a year, not in days
 *
 * A Bestia day passes in **eight real hours** (`SPEED_FACTOR = 3`, 24 h/day) and the 120-day year in forty
 * real days. So a period picked in absolute days would mean something quite different here than on Earth: a
 * three-day synoptic system would be a full week of real time. Every period below is therefore Earth's figure
 * *as a share of a year*, converted to Bestia days - which is what keeps a front feeling like a front.
 *
 * ### Measured, over a Bestia year on all 35 land regions of the 128 km reference world
 *
 * ```
 * clear        49.5%    rain          13.6%    snow        1.2%    sandstorm   1.0%
 * cloudy       23.6%    heavy rain     4.9%    blizzard    0.3%    mana storm  4.3%
 * fog           0.1%    thunderstorm   1.4%                       tornado     0.1%
 * ```
 *
 * About a fifth of days precipitate, which is what the world's own rainfall asks for. Rank correlation between
 * a region's annual rainfall and how often it actually rains there is **0.968** - that figure, not the
 * percentages, is what says the model is connected to the world at all, and `WeatherClimatologyTest` prints it
 * on every run.
 *
 * The mana-storm share is high against the thunderstorm share and is meant to be: it is concentrated almost
 * entirely in the quarter of the land above [manaStormFloor], where it is the characteristic weather rather
 * than a rare event. [manaStormFloor] is the lever if that reads as too common.
 *
 * ### It is deliberately absent from `pipelineVersion`
 *
 * `WorldParams` folds this into neither `version` nor `chunkTierVersion`, and that is the point rather than an
 * omission. Weather cannot move a voxel: there is no cached artefact keyed on it, and folding it in would make
 * retuning how often it rains refuse every existing world at the boot gate and invalidate every cached chunk.
 * The comment at the fold site says so; do not "fix" it.
 */
data class WeatherParams(

  /**
   * Wavelength of the synoptic channel - the fronts - in metres, at detail scale 1.
   *
   * Scaled by `WorldConfig.scaleByLength`, unlike the region spacing: a *front* is a feature of the world
   * whose size should track the world's, and combined with [synopticPeriodDays] it gives roughly one world
   * crossing per Bestia day at any size. On genesis that is 150 km, so about nine regions span a front and it
   * sweeps visibly rather than flickering region by region.
   */
  val synopticWavelength: Double = 600_000.0,

  /**
   * Days a synoptic system takes to pass.
   *
   * Earth's ~3.5 days is about 1% of a year; 1% of a 120-day year is 1.2 days, which is 9.6 real hours - long
   * enough that a two-hour session sees one weather regime rather than a fifth of one.
   */
  val synopticPeriodDays: Double = 1.2,

  /** Wavelength of the slower air-mass channel, in metres at detail scale 1. */
  val airmassWavelength: Double = 1_800_000.0,

  /** Days an air-mass regime lasts. Earth's fortnight is ~4% of a year, so 4.8 Bestia days. */
  val airmassPeriodDays: Double = 4.8,

  /** How much of the combined field the fronts carry, the rest being the air mass. */
  val synopticShare: Double = 0.72,

  /**
   * Millimetres continuous intensity-1 rain delivers in one Bestia day.
   *
   * **The one constant that decides whether deserts stay dry**, because it is what turns a region's monthly
   * curve into a wet-day probability. Checked against the actual layer values in `WeatherFieldTest`.
   */
  val rainRateMmPerDay: Double = 120.0,

  /** Mean intensity of a wet day, for the same conversion. */
  val meanWetIntensity: Double = 0.5,

  /** Most of the year any region may be wet, however much rain it gets. */
  val maxWetFraction: Double = 0.80,

  /**
   * Exponent that skews intensity within a wet day.
   *
   * Above one, so most rain is light and a downpour is rare. A desert's rarest event then has the same
   * *normalised* intensity as a rainforest's, which is right - a desert cloudburst really is as hard.
   */
  val rainSkew: Double = 2.0,

  /** Intensity at or above which rain is [WeatherKind.HEAVY_RAIN]. */
  val heavyRainIntensity: Double = 0.55,

  /** Cloud cover at or above which a dry sky is [WeatherKind.CLOUDY]. */
  val cloudyCover: Double = 0.55,

  /** Fog score at or above which a calm damp dawn is [WeatherKind.FOG]. */
  val fogScore: Double = 0.65,

  /** Severity at or above which a wet day is a [WeatherKind.THUNDERSTORM]. */
  val thunderstormSeverity: Double = 0.62,

  /** Severity at or above which a storm puts a [WeatherKind.TORNADO] on the ground. */
  val tornadoSeverity: Double = 0.93,

  /** Intensity at or above which frozen precipitation is a [WeatherKind.BLIZZARD]. */
  val blizzardIntensity: Double = 0.55,

  /** Wind in m/s at or above which loose ground lifts into a [WeatherKind.SANDSTORM]. */
  val sandstormWind: Double = 16.0,

  /** Share of a region that must be loose ground before a sandstorm is possible. */
  val sandstormSurfaceShare: Double = 0.40,

  /**
   * Mean mana below which mana does **nothing** to the weather.
   *
   * A threshold rather than a gain from zero, deliberately: if every region got a share of the bonus the layer
   * would be a global multiplier and "high-mana places are dangerous" would not be a thing a player could
   * learn. A threshold makes it a *place*.
   */
  val manaThreshold: Double = 0.55,

  /** Exponent on the mana excess. Superlinear, so 0.6 and 0.95 are very different places. */
  val manaExponent: Double = 2.0,

  /** Extra storm potential at full mana. At 3.0 a saturated region is four times as stormy. */
  val manaGain: Double = 3.0,

  /**
   * Mana below which a [WeatherKind.MANA_STORM] cannot happen at all, whatever the roll.
   *
   * What makes it a property of the map rather than a rare global event.
   */
  val manaStormFloor: Double = 0.75,

  /** Severity at or above which a high-mana region gets a mana storm. */
  val manaStormSeverity: Double = 0.82,

  /** Coldest a maritime region's day-night swing gets, in degrees. */
  val diurnalBase: Double = 4.0,

  /** Extra swing a fully continental region gets, in degrees. */
  val diurnalContinentality: Double = 12.0,

  /** How much of the diurnal swing a full cloud deck removes. */
  val cloudDamping: Double = 0.6,

  /** How much of the diurnal and weather terms a sheltered column keeps. */
  val shelterDamping: Double = 0.7
) : Params {

  init {
    require(synopticWavelength > 0.0) { "synopticWavelength must be positive" }
    require(synopticPeriodDays > 0.0) { "synopticPeriodDays must be positive" }
    require(airmassWavelength > 0.0) { "airmassWavelength must be positive" }
    require(airmassPeriodDays > 0.0) { "airmassPeriodDays must be positive" }
    require(synopticShare in 0.0..1.0) { "synopticShare must be a share" }
    require(rainRateMmPerDay > 0.0) { "rainRateMmPerDay must be positive" }
    require(meanWetIntensity > 0.0 && meanWetIntensity <= 1.0) { "meanWetIntensity must be in (0,1]" }
    require(maxWetFraction in 0.0..1.0) { "maxWetFraction must be a share" }
    require(rainSkew > 0.0) { "rainSkew must be positive" }
    require(heavyRainIntensity in 0.0..1.0) { "heavyRainIntensity must be a share" }
    require(cloudyCover in 0.0..1.0) { "cloudyCover must be a share" }
    require(fogScore in 0.0..1.0) { "fogScore must be a share" }
    require(thunderstormSeverity in 0.0..1.0) { "thunderstormSeverity must be a share" }
    require(tornadoSeverity in 0.0..1.0) { "tornadoSeverity must be a share" }
    require(tornadoSeverity > thunderstormSeverity) {
      "a tornado must be rarer than the thunderstorm it comes out of"
    }
    require(blizzardIntensity in 0.0..1.0) { "blizzardIntensity must be a share" }
    require(sandstormWind > 0.0) { "sandstormWind must be positive" }
    require(sandstormSurfaceShare in 0.0..1.0) { "sandstormSurfaceShare must be a share" }
    require(manaThreshold in 0.0..1.0) { "manaThreshold must be a share" }
    require(manaExponent > 0.0) { "manaExponent must be positive" }
    require(manaGain >= 0.0) { "manaGain must not be negative" }
    require(manaStormFloor in 0.0..1.0) { "manaStormFloor must be a share" }
    require(manaStormFloor > manaThreshold) {
      "a mana storm must need more mana than the ordinary bonus does, or the floor means nothing"
    }
    require(manaStormSeverity in 0.0..1.0) { "manaStormSeverity must be a share" }
    require(diurnalBase >= 0.0) { "diurnalBase must not be negative" }
    require(diurnalContinentality >= 0.0) { "diurnalContinentality must not be negative" }
    require(cloudDamping in 0.0..1.0) { "cloudDamping must be a share" }
    require(shelterDamping in 0.0..1.0) { "shelterDamping must be a share" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    synopticWavelength = source.double("synopticWavelength", synopticWavelength),
    synopticPeriodDays = source.double("synopticPeriodDays", synopticPeriodDays),
    airmassWavelength = source.double("airmassWavelength", airmassWavelength),
    airmassPeriodDays = source.double("airmassPeriodDays", airmassPeriodDays),
    synopticShare = source.double("synopticShare", synopticShare),
    rainRateMmPerDay = source.double("rainRateMmPerDay", rainRateMmPerDay),
    meanWetIntensity = source.double("meanWetIntensity", meanWetIntensity),
    maxWetFraction = source.double("maxWetFraction", maxWetFraction),
    rainSkew = source.double("rainSkew", rainSkew),
    heavyRainIntensity = source.double("heavyRainIntensity", heavyRainIntensity),
    cloudyCover = source.double("cloudyCover", cloudyCover),
    fogScore = source.double("fogScore", fogScore),
    thunderstormSeverity = source.double("thunderstormSeverity", thunderstormSeverity),
    tornadoSeverity = source.double("tornadoSeverity", tornadoSeverity),
    blizzardIntensity = source.double("blizzardIntensity", blizzardIntensity),
    sandstormWind = source.double("sandstormWind", sandstormWind),
    sandstormSurfaceShare = source.double("sandstormSurfaceShare", sandstormSurfaceShare),
    manaThreshold = source.double("manaThreshold", manaThreshold),
    manaExponent = source.double("manaExponent", manaExponent),
    manaGain = source.double("manaGain", manaGain),
    manaStormFloor = source.double("manaStormFloor", manaStormFloor),
    manaStormSeverity = source.double("manaStormSeverity", manaStormSeverity),
    diurnalBase = source.double("diurnalBase", diurnalBase),
    diurnalContinentality = source.double("diurnalContinentality", diurnalContinentality),
    cloudDamping = source.double("cloudDamping", cloudDamping),
    shelterDamping = source.double("shelterDamping", shelterDamping)
  )

  override fun digest() = ParamsDigest()
    .put("synopticWavelength", synopticWavelength)
    .put("synopticPeriodDays", synopticPeriodDays)
    .put("airmassWavelength", airmassWavelength)
    .put("airmassPeriodDays", airmassPeriodDays)
    .put("synopticShare", synopticShare)
    .put("rainRateMmPerDay", rainRateMmPerDay)
    .put("meanWetIntensity", meanWetIntensity)
    .put("maxWetFraction", maxWetFraction)
    .put("rainSkew", rainSkew)
    .put("heavyRainIntensity", heavyRainIntensity)
    .put("cloudyCover", cloudyCover)
    .put("fogScore", fogScore)
    .put("thunderstormSeverity", thunderstormSeverity)
    .put("tornadoSeverity", tornadoSeverity)
    .put("blizzardIntensity", blizzardIntensity)
    .put("sandstormWind", sandstormWind)
    .put("sandstormSurfaceShare", sandstormSurfaceShare)
    .put("manaThreshold", manaThreshold)
    .put("manaExponent", manaExponent)
    .put("manaGain", manaGain)
    .put("manaStormFloor", manaStormFloor)
    .put("manaStormSeverity", manaStormSeverity)
    .put("diurnalBase", diurnalBase)
    .put("diurnalContinentality", diurnalContinentality)
    .put("cloudDamping", cloudDamping)
    .put("shelterDamping", shelterDamping)
}
