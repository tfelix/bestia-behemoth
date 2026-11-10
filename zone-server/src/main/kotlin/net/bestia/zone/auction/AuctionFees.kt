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

  /** Rounded up to whole coins, and never free. */
  fun listingFee(price: Long): Long {
    return maxOf(1L, ceil(price * FIXED_PRICE_RATE).toLong())
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
