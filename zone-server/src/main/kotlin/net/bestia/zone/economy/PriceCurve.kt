package net.bestia.zone.economy

import kotlin.math.ln

/**
 * What a level of stock says a price ought to be.
 *
 * One formula with two readers, and they read it at different speeds. [EconomyStep] relaxes the stored
 * deviation toward it over days, which is what keeps prices continuous; [SettlementMarket] mixes a
 * little of it in unsmoothed, which is what makes clearing a shelf cost more by the time you reach the
 * bottom of it. Two copies of this would be two curves that could disagree about what a shortage is.
 */
object PriceCurve {

  /** I1, as a multiple of the reference price. Enforced by clamping, not by hoping. */
  const val FLOOR = 0.5
  const val CEILING = 4.0

  /** How hard a price reacts to being under its cover. One is proportional. */
  private const val SENSITIVITY = 0.8

  val LOG_FLOOR = ln(FLOOR)
  val LOG_CEILING = ln(CEILING)

  /**
   * The log premium this much stock justifies, clamped.
   *
   * Measured as *cover* - days of the town's own flow standing on the shelves - against the cover it
   * wants at this time of year. That framing is what makes a deviation of zero an exact fixed point:
   * at the reference the two are the same number and the logarithm is zero.
   */
  fun targetFor(commodity: Commodity, standing: Double, rate: Double, season: Double): Double {
    if (rate <= 0.0) return 0.0

    val cover = standing / rate
    // An empty store is an unbounded logarithm, and the ceiling is where it belongs anyway.
    if (cover <= 0.0) return LOG_CEILING

    return clamp(SENSITIVITY * ln(commodity.coverDays * season / cover))
  }

  fun clamp(logPremium: Double): Double {
    return logPremium.coerceIn(LOG_FLOOR, LOG_CEILING)
  }
}
