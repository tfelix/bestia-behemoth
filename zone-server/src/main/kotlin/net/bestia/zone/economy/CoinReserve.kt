package net.bestia.zone.economy

/**
 * Where coin comes from when it did not come out of somebody's purse, and where it goes when it stops
 * being in one.
 *
 * A seam for [SettlementCapacity]'s reason: a settlement's arithmetic is the same whether or not the
 * world is keeping count, and the tests about that arithmetic should not have to.
 */
interface CoinReserve {

  /** What the reserve can still pay out. Never negative. */
  fun available(): Double

  /** Moves [coins] out of the reserve, or back into it when negative. */
  fun charge(coins: Double)
}

/**
 * Bottomless, and keeps no books.
 *
 * What the economy did before the supply was finite, and still what a test gets when it is about how a
 * town recovers rather than about where the coin came from.
 */
class UnlimitedReserve : CoinReserve {

  override fun available(): Double {
    return Double.POSITIVE_INFINITY
  }

  override fun charge(coins: Double) {
  }
}
