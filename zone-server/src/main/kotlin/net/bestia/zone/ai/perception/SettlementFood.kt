package net.bestia.zone.ai.perception

import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * Where a townsperson can get a meal, and whether there is one to get.
 *
 * Behind an interface for [ForageGround]'s reason: the real answer wants a generated world and a
 * settlement ledger, and an AI scenario test has neither. What a scenario is about is whether a hungry
 * villager walks to the vendor, not which of a town's buildings it turns out to be.
 */
fun interface SettlementFood {

  /** The nearest counter to [at], or null out in the country and in a hamlet with no trade at all. */
  fun stallNear(at: Vec3L): Stall?

  /** @param inStock whether there is anything on the shelf today - what a burnt field takes away */
  class Stall(val doorstep: Vec3L, val inStock: Boolean)
}

/**
 * The real answer: a counter in the settlement underfoot, and its ledger's own bread.
 *
 * Stock rather than what is *offered*, and the difference matters here more than anywhere: the offer is
 * the surplus above what the locals keep back, and a resident eating is exactly the local the reserve
 * exists for. Reading the offer would have a town starve while its own larder was full.
 */
@Service
class SettlementFoodStalls(
  private val sites: SettlementSiteIndex,
  private val economy: SettlementEconomyService,
) : SettlementFood {

  override fun stallNear(at: Vec3L): SettlementFood.Stall? {
    val site = sites.siteCovering(at.x, at.y) ?: return null
    val counter = counterIn(site, at) ?: return null
    val larder = economy.marketOf(site.index)?.stockOf(STAPLE) ?: 0.0

    return SettlementFood.Stall(sites.doorstepOf(counter), larder >= 1.0)
  }

  /**
   * Somewhere a meal changes hands, nearest first.
   *
   * Four trades in preference order rather than one, because the one that exists differs by town: a
   * city has a market, a village a general store, and a hamlet only ever the inn - which
   * `BusinessCatalogue` gives `alwaysAtLeastOne`, so almost nowhere comes back empty.
   */
  private fun counterIn(site: SettlementSite, at: Vec3L): SettlementSite.Building? {
    for (trade in COUNTERS) {
      val type = BusinessCatalogue.ALL.indexOfFirst { it.id == trade }
      val nearest = site.buildingsFor(type).minByOrNull { sites.doorstepOf(it).distance(at) }
      if (nearest != null) return nearest
    }

    return null
  }

  private companion object {
    /** What a resident's hunger is measured against. The end of the only chain there is. */
    const val STAPLE = "bread"

    val COUNTERS = listOf("market_trader", "general_store", "inn", "baker")
  }
}
