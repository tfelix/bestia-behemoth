package net.bestia.zone.economy

/**
 * What a settlement's treasury holds when it sits at its reference.
 *
 * A seam for [SettlementCapacity]'s reason: a real treasury is a share of a world-wide coin supply, and
 * the tests that are about the step's arithmetic should not have to build a world to get one.
 */
fun interface MoneySupply {

  fun treasuryFor(population: Int, wealth: Double): Double
}

/**
 * A fixed amount per resident, tilted by wealth.
 *
 * Has the same shape as a real share, so anything that turns on a poor town keeping a smaller purse than
 * a rich one behaves the same way against it. What it does not do is sum to a known total, which is
 * precisely why the server uses [WorldMoneySupply] instead.
 */
class PerResidentTreasury(private val perResident: Double = COINS_PER_RESIDENT) : MoneySupply {

  override fun treasuryFor(population: Int, wealth: Double): Double {
    return population * perResident * (0.5 + wealth)
  }

  companion object {
    const val COINS_PER_RESIDENT = 12.0

    val DEFAULT = PerResidentTreasury()
  }
}
