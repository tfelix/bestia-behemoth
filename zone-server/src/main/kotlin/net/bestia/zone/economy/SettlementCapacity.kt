package net.bestia.zone.economy

import org.springframework.stereotype.Service

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
}

/** Nothing is damaged yet, so every trade runs at its reference. Replaced by the damage sweep. */
@Service
class UndamagedCapacity : SettlementCapacity {
  override fun capacityOf(settlement: Int, trade: String): Double {
    return 1.0
  }
}
