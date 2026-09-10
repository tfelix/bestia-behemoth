package net.bestia.zone.economy

import kotlin.math.abs

/**
 * Everything a settlement's books actually hold: how far it is from where it ought to be.
 *
 * Deviations rather than levels, and that is the decision the whole tier hangs off. A settlement nobody
 * has touched sits at zero, its step is exactly a no-op, and it needs no database row at all - so
 * "simulate the whole world" and "persist only what players touched" stop being a trade-off.
 *
 * @param deltaStock units above or below the reference stock, per commodity
 * @param deltaLogPrice log-price deviation, per commodity. Log because price moves multiplicatively and
 *   because the bound that keeps it sane - I1's half to four times - is symmetric there
 * @param lastStepDay the game day the books were brought up to. Whole days only, so a fraction of a day
 *   is carried rather than lost; see [EconomyStep]
 */
data class LedgerState(
  val deltaStock: Map<String, Double> = emptyMap(),
  val deltaLogPrice: Map<String, Double> = emptyMap(),
  val treasury: Double = 0.0,
  val lastStepDay: Double = 0.0,
) {

  /**
   * Whether this is close enough to the reference that no row is worth keeping.
   *
   * The test the *writer* applies, never a reader: I18 says a player standing in a town changes nothing,
   * and a read path able to create a row would break that however carefully the step is written.
   *
   * A tolerance rather than an equality because relaxation is exponential and never quite arrives, and
   * because the price target divides two numbers that are equal in arithmetic and not in floating point.
   */
  fun isNegligible(
    stockTolerance: Double,
    priceTolerance: Double,
    treasuryReference: Double,
    treasuryTolerance: Double,
  ): Boolean {
    return deltaStock.values.all { abs(it) < stockTolerance } &&
      deltaLogPrice.values.all { abs(it) < priceTolerance } &&
      abs(treasury - treasuryReference) < treasuryReference * treasuryTolerance
  }
}
