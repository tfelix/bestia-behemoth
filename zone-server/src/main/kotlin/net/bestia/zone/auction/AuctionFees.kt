package net.bestia.zone.auction

import kotlin.math.ceil

/**
 * What putting goods up costs, from the docs' economy page: a share of the asking price, paid up front. The
 * listing fee returns to the world reserve rather than being burned. A player house adds its owner's fee, half of
 * which goes to its till and half to NPCs; the start towns' houses have no owner, so no owner fee.
 */
object AuctionFees {

  const val FIXED_PRICE_RATE = 0.10

  const val MIN_OWNER_FEE_PERCENT = 1
  const val OWNER_FEE_PERCENT_PER_LEVEL = 5

  /** A fixed-price listing's fee. */
  fun listingFee(price: Long): Long {
    return listingFee(price, days = 0)
  }

  /** An auction's listing fee by how many days it runs, on the starting bid. */
  val AUCTION_RATE_BY_DAYS: Map<Int, Double> = mapOf(1 to 0.06, 2 to 0.10, 5 to 0.20)

  /** [days] 0 is a fixed price. Rounded up, and never free. */
  fun listingFee(price: Long, days: Int): Long {
    val rate = if (days == 0) FIXED_PRICE_RATE else AUCTION_RATE_BY_DAYS.getValue(days)

    return maxOf(1L, ceil(price * rate).toLong())
  }

  /** Rounded up like every fee; nothing at a house with no owner. */
  fun ownerFee(price: Long, percent: Int): Long {
    if (percent <= 0) return 0

    return maxOf(1L, ceil(price * percent / 100.0).toLong())
  }

  /** What reaches the owner's till. The NPCs' half is the rest, so an odd coin goes to them. */
  fun tillShare(ownerFee: Long): Long {
    return ownerFee / 2
  }

  /** The highest owner fee Trade Post Owner at [level] allows. */
  fun maxOwnerFeePercent(level: Int): Int {
    return OWNER_FEE_PERCENT_PER_LEVEL * level
  }
}
