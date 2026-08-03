package net.bestia.worldgen.climate

import kotlin.math.sin

/**
 * The orbital phase, and the one place the year is turned into a number.
 *
 * There are three notions of "when in the year" in this codebase and until this file they were unrelated:
 *
 * - [ClimateStage] steps a **quarter index** `0..3` and turns it into a phase to shift the wind belts and
 *   sign the seasonal warming.
 * - [SeasonalPrecipitation] reads those four quarters back at their **centres in a twelve-month year**,
 *   months 1.5, 4.5, 7.5 and 10.5.
 * - The runtime clock (`zone-server`'s `BestiaDateTime`) counts a **four-month year** of thirty days each.
 *
 * Two of those say "month" and mean different things, and the third is an index. Mixing any two of them is
 * silent: the rain arrives a third of a year early, or in the wrong hemisphere, and every test still passes.
 * So the **orbital phase is the authority** and every other quantity is derived from it here rather than
 * re-derived at each call site.
 *
 * ### The generator and the runtime agree exactly, and that is provable
 *
 * `ClimateStage` samples quarter `s` at phase `TAU * s / 4`. This file puts quarter `s`'s centre at
 * `yearProgress = (2s + 1) / 8`, and
 *
 * ```
 * orbitalPhase((2s+1)/8) = TAU * ((2s+1)/8 - 1/8) = TAU * 2s/8 = TAU * s / 4
 * ```
 *
 * which is the stage's own phase. The eighth-of-a-year offset is not a fudge factor - it is the half-quarter
 * that turns "the quarter starting here" into "the quarter centred here", which is the same shift
 * [SeasonalPrecipitation.atMonth] applies for the same reason. So a runtime asking for the warming at a
 * quarter centre gets the number the generator used, and asking between centres interpolates along the curve
 * the generator sampled rather than along a different one that happens to pass through the same four points.
 *
 * Pure, stateless and free of any clock, like the rest of `worldgen`. The runtime supplies the time.
 */
object Seasons {

  /** One full turn. Every phase here is a fraction of it. */
  const val TAU = 2.0 * Math.PI

  /**
   * Quarters in a year. Not a tunable: it is [SeasonalPrecipitation.COUNT], which is how many layers are on
   * disk, and `ClimateParams.seasons` is already pinned to the same number by a `require`.
   */
  const val QUARTERS = 4

  /**
   * Phase of quarter [quarter]'s sample, in radians.
   *
   * Kept as the literal expression [ClimateStage] used before this file existed, in the same order, so
   * substituting the call is bit-identical rather than merely equal to within a rounding error. Do not
   * "simplify" it.
   */
  fun phaseOfQuarter(quarter: Int, quarters: Int = QUARTERS): Double = TAU * quarter / quarters

  /**
   * Fraction of the year at the centre of quarter [quarter]: 1/8, 3/8, 5/8, 7/8.
   *
   * The centre rather than the start, because a quarterly total is a statement about a whole quarter and the
   * value it interpolates to is the quarter's midpoint. Getting this wrong shifts the whole annual cycle by
   * six weeks, in the same direction everywhere, which reads as a world whose seasons are merely offset -
   * plausible, and wrong.
   */
  fun quarterCentreProgress(quarter: Int, quarters: Int = QUARTERS): Double =
    (2.0 * quarter + 1.0) / (2.0 * quarters)

  /**
   * Orbital phase at a fraction of the year, in radians. Zero at the spring quarter's centre.
   *
   * [yearProgress] is periodic and is not normalised here: 1.25 is the same phase as 0.25, which is what a
   * caller stepping a day counter forward across a new year needs.
   */
  fun orbitalPhase(yearProgress: Double, quarters: Int = QUARTERS): Double =
    TAU * (yearProgress - 1.0 / (2.0 * quarters))

  /**
   * How far the northern hemisphere sits from its annual mean, as a signed fraction of the local
   * summer-to-winter swing: `+0.5` at midsummer, `-0.5` at midwinter, zero at the equinoxes.
   *
   * The southern hemisphere is the negation - see [warmingAt], which is the function almost every caller
   * actually wants.
   */
  fun northernWarming(yearProgress: Double, quarters: Int = QUARTERS): Double =
    warmingAtPhase(orbitalPhase(yearProgress, quarters))

  /** [northernWarming] from a phase that has already been computed. The generator's own expression. */
  fun warmingAtPhase(phase: Double): Double = 0.5 * sin(phase)

  /**
   * `+1` in the northern hemisphere, `-1` in the southern.
   *
   * The flip is at [ClimateStage.latitudeOf]`(northwards) == 0`, i.e. at `northwards == 0.5`, and that is
   * **independent of `polewardLatitude`** - the mapping is a linear ramp through the origin, so scaling it
   * cannot move where it changes sign. So unlike [Winds.directionAt], which needs the true latitude, the
   * hemisphere sign can be answered without the configured value and cannot be got wrong by a caller that
   * does not have it.
   *
   * @param northwards fraction from the world's south edge to its north edge
   */
  fun hemisphereSign(northwards: Double): Double = if (northwards >= 0.5) 1.0 else -1.0

  /**
   * The seasonal warming term at a place and a time, signed for its hemisphere.
   *
   * The hemispheres are half a year apart, so the same orbital phase warms one and cools the other. Without
   * the sign the whole world has its summer at once and a southern monsoon arrives in the wrong half of the
   * year - which is the thing four seasonal fields exist to express.
   */
  fun warmingAt(yearProgress: Double, northwards: Double, quarters: Int = QUARTERS): Double =
    northernWarming(yearProgress, quarters) * hemisphereSign(northwards)

  /**
   * The month [SeasonalPrecipitation.atMonth] expects, from a fraction of the year.
   *
   * **The bridge between the twelve-month climate year and the runtime's four-month one.** Handing
   * `BestiaDateTime.month` (1..4) to `atMonth` compiles, runs, and reads a point one third of the way into
   * the year from where the caller meant - so this conversion exists to be the only place the two calendars
   * meet.
   */
  fun climateMonthOf(yearProgress: Double): Double = yearProgress * ClimateStage.MONTHS_PER_YEAR

  /**
   * Which quarter a fraction of the year falls in, as an index into [SeasonalPrecipitation.LAYERS].
   *
   * Quarter *boundaries*, not centres: `yearProgress` in `[0, 0.25)` is quarter 0. A caller wanting the
   * quarter whose sample is nearest wants [quarterCentreProgress] and a rounding, not this.
   */
  fun quarterOf(yearProgress: Double, quarters: Int = QUARTERS): Int {
    val scaled = Math.floor(yearProgress * quarters).toInt()
    return Math.floorMod(scaled, quarters)
  }
}
