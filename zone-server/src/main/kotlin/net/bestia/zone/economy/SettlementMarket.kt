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
   * I1: between half and four times the reference, because the deviation it is built from is clamped
   * there every step rather than being hoped about here.
   *
   * The seasonal term is derived from the stock season rather than authored separately - a glut is
   * cheap, and saying that twice would let the two curves disagree about when the harvest is.
   */
  fun priceOf(commodity: String): Double {
    val good = catalogue.commodityOrThrow(commodity)
    val season = good.seasonAt(dayOfYear)
    val delta = state.deltaLogPrice[commodity] ?: 0.0

    return good.refPrice *
      reference.priceMultiplier *
      Math.pow(season, -SEASON_PRICE_ELASTICITY) *
      exp(delta)
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

  private fun expectedStock(commodity: String): Double {
    return (reference.stock[commodity] ?: 0.0) * catalogue.commodityOrThrow(commodity).seasonAt(dayOfYear)
  }

  companion object {
    /** Days of its own supply a settlement keeps back before anything is for sale. */
    const val LOCAL_RESERVE_DAYS = 5.0

    /** How hard a glut moves the price down. Below one, so the harvest is cheaper without being free. */
    private const val SEASON_PRICE_ELASTICITY = 0.6
  }
}
