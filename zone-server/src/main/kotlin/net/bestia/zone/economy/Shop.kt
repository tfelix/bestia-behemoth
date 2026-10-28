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

  /** One line of a basket. A null [commodity] is something this counter does not deal in. */
  class BasketLine(val commodity: Commodity?, val units: Int, val selling: Boolean)

  /**
   * What a basket would come to, or why it cannot happen.
   *
   * @param balance positive when the town pays the player, negative when the player pays
   * @param refusedLine the line at fault, or null when the basket as a whole is refused
   */
  class BasketQuote(
    val lineCoins: List<Long>,
    val balance: Long,
    val refusal: Refusal? = null,
    val refusedLine: Int? = null,
  )

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
    return quoteOne(BasketLine(commodity, units, selling = false))
  }

  /** What the player is paid for [units], and whether the town can find the coin at all. */
  fun quoteSell(commodity: Commodity?, units: Int): Quote {
    return quoteOne(BasketLine(commodity, units, selling = true))
  }

  /**
   * Every line priced on its own, and the treasury checked against the **balance** alone: only the
   * difference changes hands, so a town with an empty purse can still take grain in exchange for flour.
   */
  fun quoteBasket(lines: List<BasketLine>): BasketQuote {
    val lineCoins = ArrayList<Long>(lines.size)

    lines.forEachIndexed { index, line ->
      val refusal = stockRefusal(line)
      if (refusal != null) return BasketQuote(emptyList(), 0, refusal, index)

      lineCoins += line.coins()
    }

    val balance = lines.indices.sumOf { if (lines[it].selling) lineCoins[it] else -lineCoins[it] }
    val treasuryAfter = market.treasuryWith(-balance)

    // I8: a shop that cannot pay refuses; it never pays with money it has not got.
    if (balance > 0 && treasuryAfter < 0.0) return BasketQuote(emptyList(), 0, Refusal.TREASURY_EMPTY)
    // I9: the town cannot hold more than its ceiling, and refusing is honest where a collapsing price
    // would still be an income.
    if (balance < 0 && treasuryAfter > treasuryCap()) return BasketQuote(emptyList(), 0, Refusal.TREASURY_FULL)

    return BasketQuote(lineCoins, balance)
  }

  private fun quoteOne(line: BasketLine): Quote {
    val quote = quoteBasket(listOf(line))
    if (quote.refusal != null) return Quote(0, 0, quote.refusal)

    return Quote(line.units, quote.lineCoins.single())
  }

  private fun stockRefusal(line: BasketLine): Refusal? {
    val commodity = line.commodity ?: return Refusal.NOT_STOCKED

    return if (line.selling) {
      // I4, from the selling side: what the warehouse cannot hold would spoil on the pile the same day.
      val room = reference.warehouseCap(commodity.id) - market.stockOf(commodity.id)
      if (line.units > room) Refusal.OUT_OF_STOCK else null
    } else {
      val offered = floor(market.offerableOf(commodity.id)).toInt()
      if (line.units > offered) Refusal.OUT_OF_STOCK else null
    }
  }

  private fun BasketLine.coins(): Long {
    val good = commodity ?: return 0

    return if (selling) sellTotal(good, units) else buyTotal(good, units)
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
