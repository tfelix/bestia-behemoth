package net.bestia.zone.economy

/**
 * What one trader gets on top of a town's prices: the factor on what they pay, and on what they are paid.
 *
 * The buy factor never drops below [MIN_BUY_FACTOR] and the sell factor never rises above [MAX_SELL_FACTOR], so no
 * trader can close the spread a shop always keeps between buying and selling.
 */
class PersonalPrice(buyFactor: Double, sellFactor: Double) {
  val buyFactor: Double = buyFactor.coerceAtLeast(MIN_BUY_FACTOR)
  val sellFactor: Double = sellFactor.coerceIn(0.0, MAX_SELL_FACTOR)

  /** A Haggling Trader pays [share] less and is paid [share] more, on top of everything else. */
  fun haggled(share: Double): PersonalPrice = PersonalPrice(buyFactor * (1.0 - share), sellFactor * (1.0 + share))

  companion object {
    val NEUTRAL = PersonalPrice(1.0, 1.0)

    /**
     * Inside [Shop.SPREAD] together: at both bounds a round trip still loses about 2%, since
     * `0.88 * (1 + 0.12) > 1.10 * (1 - 0.12)`. Wide enough for the best honor and Haggling Lv5 to both count.
     */
    const val MIN_BUY_FACTOR = 0.88
    const val MAX_SELL_FACTOR = 1.10
  }
}
