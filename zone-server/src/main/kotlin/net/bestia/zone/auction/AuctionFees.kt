package net.bestia.zone.auction

import kotlin.math.ceil

/**
 * What putting goods up costs, from the docs' economy page: a share of the asking price, paid up front and
 * returned to the world reserve rather than burned. The start towns' houses have no owner, so no owner fee.
 */
object AuctionFees {

  const val FIXED_PRICE_RATE = 0.10

  /** Rounded up to whole coins, and never free. */
  fun listingFee(price: Long): Long {
    return maxOf(1L, ceil(price * FIXED_PRICE_RATE).toLong())
  }
}
