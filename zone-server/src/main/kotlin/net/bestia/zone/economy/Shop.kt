package net.bestia.zone.economy

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * What one settlement will trade right now, and what it charges.
 *
 * A projection, not a container. It is derived from the market whenever somebody asks and nothing is ever
 * written back to it - so there is no despawn path, no reconciliation, and no shop left holding items
 * after the person keeping it went home.
 *
 * ### Settled unit by unit, never against a snapshot
 *
 * A quote for twenty loaves is twenty prices summed, each one a little dearer than the last, because each
 * loaf is priced against a town with one fewer loaf in it. That is what makes arbitrage extinguish itself
 * rather than scale: the marginal price converges on the other town's, and the tenth cartload is not
 * worth the journey. Buying at one posted price would make the profit linear in how much you can carry.
 */
class Shop(
  private val catalogue: EconomyCatalogue,
  private val market: SettlementMarket,
  private val reference: SettlementReference,
) {

  /** What a trade would come to, or why it cannot happen. */
  class Quote(val units: Int, val coins: Long, val refusal: Refusal? = null) {
    val allowed: Boolean get() = refusal == null
  }

  enum class Refusal { NOT_STOCKED, OUT_OF_STOCK, TREASURY_EMPTY, TREASURY_FULL }

  class Offer(val commodity: Commodity, val offered: Int, val buyPrice: Long, val sellPrice: Long)

  fun offers(): List<Offer> {
    return catalogue.topological.map { commodity ->
      Offer(
        commodity = commodity,
        offered = floor(market.offerableOf(commodity.id)).toInt(),
        buyPrice = buyTotal(commodity, 1),
        sellPrice = sellTotal(commodity, 1),
      )
    }
  }

  /** What the player pays for [units]. Refused rather than trimmed: a short fill is a surprise. */
  fun quoteBuy(commodity: Commodity?, units: Int): Quote {
    if (commodity == null) return Quote(0, 0, Refusal.NOT_STOCKED)

    val offered = floor(market.offerableOf(commodity.id)).toInt()
    if (units > offered) return Quote(0, 0, Refusal.OUT_OF_STOCK)

    val coins = buyTotal(commodity, units)

    // The town cannot hold more than its ceiling, and refusing is honest where a collapsing price would
    // still be an income. I9, from the buying side: coin comes in, so the ceiling is what bites.
    if (market.treasuryWith(coins) > treasuryCap()) return Quote(0, 0, Refusal.TREASURY_FULL)

    return Quote(units, coins)
  }

  /** What the player is paid for [units], and whether the town can find the coin at all. */
  fun quoteSell(commodity: Commodity?, units: Int): Quote {
    if (commodity == null) return Quote(0, 0, Refusal.NOT_STOCKED)

    val coins = sellTotal(commodity, units)

    // I8. A shop that cannot pay refuses; it never pays with money it has not got.
    if (market.treasuryWith(-coins) < 0.0) return Quote(0, 0, Refusal.TREASURY_EMPTY)

    // I4, from the selling side: what the warehouse cannot hold would spoil on the pile the same day.
    val room = reference.warehouseCap(commodity.id) - market.stockOf(commodity.id)
    if (units > room) return Quote(0, 0, Refusal.OUT_OF_STOCK)

    return Quote(units, coins)
  }

  /**
   * Every unit priced separately and the *sum* rounded, up and to at least a coin.
   *
   * Rounding once rather than per unit because it is one payment. Rounding each unit would put up to a
   * coin of noise on every one of them, which at bread's price is a tenth of the good and would swamp
   * the impact it is meant to sit on top of.
   *
   * Both roundings go the town's way. A sub-coin gap between the two directions would be an arbitrage
   * of exactly the kind the spread exists to close, and free is not a price.
   */
  private fun buyTotal(commodity: Commodity, units: Int): Long {
    val price = market.priceOf(commodity.id) * (1.0 + SPREAD)
    val total = (0 until units).sumOf { price * impactOf(commodity, it) }

    return max(units.toLong(), ceil(total).toLong())
  }

  /** Rounded down, and never below nothing: a town pays no coin at all for something worthless. */
  private fun sellTotal(commodity: Commodity, units: Int): Long {
    val price = market.priceOf(commodity.id) * (1.0 - SPREAD)
    val total = (0 until units).sumOf { price / impactOf(commodity, it) }

    return max(0L, floor(total).toLong())
  }

  /**
   * How far the price has moved by the time this unit is reached.
   *
   * Measured against the town's own daily flow rather than against its stock, so the same purchase is a
   * shock to a hamlet and nothing to a city - which is the difference a merchant is meant to notice.
   */
  private fun impactOf(commodity: Commodity, unitsSoFar: Int): Double {
    val daily = max(1.0, reference.throughput[commodity.id] ?: 0.0)

    return 1.0 + IMPACT_PER_DAY_TRADED * (unitsSoFar / daily)
  }

  private fun treasuryCap(): Double {
    return reference.treasury * TREASURY_CAP_MULTIPLE
  }

  companion object {
    /**
     * Half the gap between what a town pays and what it charges.
     *
     * What makes buying and immediately selling back a loss. The design states that as
     * `bid <= ask * (1 - 2 * spread)`, which no symmetric quote can satisfy for any positive spread -
     * substituting gives `1 - s <= 1 - s - 2s²`. What is meant, and what holds here, is that a round
     * trip in one settlement is strictly loss-making: the player loses `2 * SPREAD` of the price on it.
     */
    const val SPREAD = 0.12

    /** How far the price moves when a trade takes a whole day of the town's flow. */
    const val IMPACT_PER_DAY_TRADED = 0.35

    /** Times the reference, above which a town has no room to take on more coin. */
    const val TREASURY_CAP_MULTIPLE = 3.0
  }
}
