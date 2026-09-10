package net.bestia.zone.economy

/**
 * How much of its reference output a trade can still manage, from 0 to 1.
 *
 * The seam player destruction arrives through. It is a parameter of [EconomyStep] from the start rather
 * than something the damage sweep adds later, because widening a signature that a semigroup property is
 * pinned against means re-proving the property.
 */
fun interface SettlementCapacity {

  /** @param trade a [Trade.id], not a business - a trade is what damage is eventually mapped onto */
  fun capacityOf(settlement: Int, trade: String): Double

  /**
   * Settlements currently under damage, so the ledger can step them without being asked.
   *
   * The ledger is caught up lazily, which works because an untouched settlement is *at* its reference
   * and stepping it is a no-op. A damaged one is not: it is diverging, and with nobody to ask about it
   * it would sit at full price with its fields burnt until somebody happened to walk in. This is what
   * pulls it into the sweep - the one place the lazy scheme needs a push.
   */
  fun damagedSettlements(): Set<Int> {
    return emptySet()
  }
}

/**
 * Every trade at its reference. [SettlementDamage] is what the server runs; this is for the tests that
 * are about the step's arithmetic rather than about what a player has knocked down.
 */
class UndamagedCapacity : SettlementCapacity {
  override fun capacityOf(settlement: Int, trade: String): Double {
    return 1.0
  }
}
