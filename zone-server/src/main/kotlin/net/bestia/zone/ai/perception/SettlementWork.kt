package net.bestia.zone.ai.perception

import net.bestia.zone.economy.EconomyCatalogue
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.Trade
import org.springframework.stereotype.Service

/**
 * Whether a townsperson's trade has anything to work with today.
 *
 * Behind an interface for [SettlementFood]'s reason: the real answer wants a generated world and a
 * settlement ledger, and an AI scenario test has neither.
 */
interface SettlementWork {

  /** What the holder of [business] makes, or null for a trade the economy does not model. */
  fun tradeOf(business: String?): Trade?

  /** Whether [settlement] can put everything [trade] consumes in front of a worker right now. */
  fun canSupply(settlement: Int, trade: Trade): Boolean
}

/**
 * The real answer, off the settlement's own shelves.
 *
 * Stock rather than what is *offered*, for the reason [SettlementFoodStalls] reads stock: the reserve
 * held back from sale is exactly what the town's own mill and bakery draw on, and reading the offer
 * would idle a workshop standing next to a full store.
 */
@Service
class SettlementWorkshops(
  private val catalogue: EconomyCatalogue,
  private val economy: SettlementEconomyService,
) : SettlementWork {

  override fun tradeOf(business: String?): Trade? {
    return business?.let { catalogue.tradeOfBusiness(it) }
  }

  override fun canSupply(settlement: Int, trade: Trade): Boolean {
    if (trade.consumes.isEmpty()) return true
    val market = economy.marketOf(settlement) ?: return false

    return trade.consumes.all { market.stockOf(it.commodity) >= it.perUnit }
  }
}
