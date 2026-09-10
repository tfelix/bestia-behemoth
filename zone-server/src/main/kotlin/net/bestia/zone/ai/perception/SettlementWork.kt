package net.bestia.zone.ai.perception

import net.bestia.zone.economy.EconomyCatalogue
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.Trade
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.settlement.SettlementSiteIndex
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

  /**
   * Where the holder of [business] fetches what they sell, for somebody who sells but does not make.
   *
   * Null for everybody else, which is most people: a baker's flour arrives through their own recipe and a
   * guard sells nothing at all.
   */
  fun supplierNear(at: Vec3L, business: String?): Supply?

  /** @param inStock whether the maker has any to hand over - what a burnt field takes away */
  class Supply(val doorstep: Vec3L, val inStock: Boolean)
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
  private val sites: SettlementSiteIndex,
) : SettlementWork {

  override fun tradeOf(business: String?): Trade? {
    return business?.let { catalogue.tradeOfBusiness(it) }
  }

  override fun canSupply(settlement: Int, trade: Trade): Boolean {
    if (trade.consumes.isEmpty()) return true
    val market = economy.marketOf(settlement) ?: return false

    return trade.consumes.all { market.stockOf(it.commodity) >= it.perUnit }
  }

  /**
   * The nearest workshop that makes the staple, for a shop that only sells it.
   *
   * Retail is the one kind of business with a supplier: everybody else either makes what they sell or
   * sells nothing. The staple is what a town's shops are for - it is the end of the only chain there is -
   * so the errand is always the same one, and it is the errand that stops when the bakery has nothing.
   */
  override fun supplierNear(at: Vec3L, business: String?): SettlementWork.Supply? {
    if (business == null || business !in catalogue.retailTrades()) return null

    val site = sites.siteCovering(at.x, at.y) ?: return null
    val maker = catalogue.producerOf(STAPLE)?.business ?: return null
    val counter = site.buildingsFor(catalogue.businessTypeOf(maker))
      .minByOrNull { sites.doorstepOf(it).distance(at) } ?: return null

    val made = economy.marketOf(site.index)?.stockOf(STAPLE) ?: 0.0

    return SettlementWork.Supply(sites.doorstepOf(counter), made >= 1.0)
  }

  private companion object {
    /** What a town's shops are for, and the only chain the catalogue models end to end. */
    const val STAPLE = "bread"
  }
}
