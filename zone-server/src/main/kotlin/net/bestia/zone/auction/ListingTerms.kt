package net.bestia.zone.auction

/**
 * What a seller asks: a fixed [price], or with [days] 1, 2 or 5 an auction that starts at [price] and may end at
 * once on a [buyout].
 */
data class ListingTerms(val price: Long, val days: Int = 0, val buyout: Long? = null) {

  val isAuction: Boolean
    get() {
      return days != 0
    }

  /** Whether an honest client could have sent these: a known length, and a buyout above the starting bid. */
  val isValid: Boolean
    get() {
      val knownLength = days == 0 || days in AuctionFees.AUCTION_RATE_BY_DAYS
      val sensibleBuyout = buyout == null || (isAuction && buyout > price)

      return knownLength && sensibleBuyout
    }
}
