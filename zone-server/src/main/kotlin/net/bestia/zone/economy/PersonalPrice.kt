package net.bestia.zone.economy

/**
 * What one trader gets on top of a town's prices: the factor on what they pay, and on what they are paid.
 *
 * The buy factor never drops below [MIN_BUY_FACTOR] and the sell factor never rises above 1, so no trader can
 * close the spread a shop always keeps between buying and selling.
 */
class PersonalPrice(buyFactor: Double, sellFactor: Double) {
  val buyFactor: Double = buyFactor.coerceAtLeast(MIN_BUY_FACTOR)
  val sellFactor: Double = sellFactor.coerceIn(0.0, 1.0)

  companion object {
    val NEUTRAL = PersonalPrice(1.0, 1.0)

    /** Well inside [Shop.SPREAD]: even the best customer still pays more than the shop would pay them. */
    const val MIN_BUY_FACTOR = 0.95
  }
}
