package net.bestia.zone.economy

import kotlin.math.exp
import kotlin.math.max

/**
 * What a settlement's shelves look like on one day: the reference, plus whatever the ledger says it is
 * off by.
 *
 * A value, computed on demand and written nowhere. That is what makes I18 - a player standing in a town
 * changes nothing - structural rather than something the read paths have to remember: there is no method
 * here that could create a row.
 */
class SettlementMarket(
  private val catalogue: EconomyCatalogue,
  private val reference: SettlementReference,
  private val state: LedgerState,
  private val dayOfYear: Double,
) {

  /** I3 and I4: never negative, never more than the warehouse holds. */
  fun stockOf(commodity: String): Double {
    val expected = expectedStock(commodity)
    val delta = state.deltaStock[commodity] ?: 0.0

    return (expected + delta).coerceIn(0.0, reference.warehouseCap(commodity))
  }

  /**
   * I1: between half and four times the reference, because the premium is clamped here as well as in
   * the step that feeds it.
   *
   * ### A little of the price moves at once
   *
   * The stored deviation only relaxes on a daily step, and a shop whose price does not budge when a
   * player clears its shelf is the thing they notice first. So the posted price is the stored deviation
   * with [PROMPTNESS] of the way to what the stock on hand says it should be - the rest arrives over the
   * next days as the deviation catches up. Continuous either way: a purchase moves the stock by one
   * unit, not by a step, so there is no jump for I2 to worry about.
   *
   * The seasonal term is derived from the stock season rather than authored separately - a glut is
   * cheap, and saying that twice would let the two curves disagree about when the harvest is.
   */
  fun priceOf(commodity: String): Double {
    val good = catalogue.commodityOrThrow(commodity)
    val season = good.seasonAt(dayOfYear)
    val settled = state.deltaLogPrice[commodity] ?: 0.0

    val prompt = PriceCurve.targetFor(good, stockOf(commodity), reference.throughput[commodity] ?: 0.0, season)
    val premium = PriceCurve.clamp(settled + PROMPTNESS * (prompt - settled))

    return good.refPrice * reference.priceMultiplier * Math.pow(season, -SEASON_PRICE_ELASTICITY) * exp(premium)
  }

  /**
   * I12: only what is left after the locals have kept back their own is ever on offer.
   *
   * The answer to buying out every shop, and it is what a caravan would find too rather than a rule
   * aimed at players. Held against throughput rather than against what residents eat, so a mill's grain
   * is reserved for the mill as well as a household's bread for the household.
   */
  fun offerableOf(commodity: String): Double {
    val reserve = (reference.throughput[commodity] ?: 0.0) * LOCAL_RESERVE_DAYS

    return max(0.0, stockOf(commodity) - reserve)
  }

  /** The treasury after [change] coins move, for a shop deciding whether it can afford to. */
  fun treasuryWith(change: Long): Double {
    return state.treasury + change
  }

  private fun expectedStock(commodity: String): Double {
    return (reference.stock[commodity] ?: 0.0) * catalogue.commodityOrThrow(commodity).seasonAt(dayOfYear)
  }

  companion object {
    /**
     * Days of its own supply a settlement keeps back before anything is for sale.
     *
     * Must stay under every commodity's cover, or that good is never for sale in any town in the world -
     * [EconomyCatalogue] refuses a catalogue where it is not. Bread was the case that found this: at
     * three days of cover against a five-day reserve, a bakery town had no loaf to sell anybody.
     */
    const val LOCAL_RESERVE_DAYS = 1.5

    /** How hard a glut moves the price down. Below one, so the harvest is cheaper without being free. */
    private const val SEASON_PRICE_ELASTICITY = 0.6

    /** How much of what the stock on hand says is felt at once, before the ledger has caught up. */
    private const val PROMPTNESS = 0.25
  }
}
