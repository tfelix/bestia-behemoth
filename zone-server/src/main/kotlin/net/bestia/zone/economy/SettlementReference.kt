package net.bestia.zone.economy

import kotlin.math.pow

/**
 * Where a settlement's economy sits when nothing has happened to it - derived from the world, stored
 * nowhere.
 *
 * The reference path the ledger holds a deviation against. Because it is a pure function of the
 * settlement, an untouched town costs nothing at all: no row, no drift, and its whole economy is
 * recomputed from a population and a roster whenever somebody finally asks.
 *
 * @param throughput units of each commodity flowing through per game-day
 * @param stock how much is standing, which is [throughput] times the commodity's days of cover
 * @param kappa how fast trade with the outside world drains a shortage or a glut, per game-day
 */
class SettlementReference(
  val settlement: Int,
  val population: Int,
  val throughput: Map<String, Double>,
  val stock: Map<String, Double>,
  val priceMultiplier: Double,
  val kappa: Double,
  val treasury: Double,
) {

  /** I4's ceiling: what a town can physically hold before the excess spoils on the pile. */
  fun warehouseCap(commodity: String): Double {
    return (stock[commodity] ?: 0.0) * WAREHOUSE_MULTIPLE
  }

  companion object {

    /**
     * Days of cover a warehouse holds before the excess is on the ground.
     *
     * Three times the reference rather than a separate authored number: what a settlement builds storage
     * for is the harvest it expects, and the seasonal peak is already inside that.
     */
    const val WAREHOUSE_MULTIPLE = 3.0

    /**
     * Everything a settlement of this size and connectedness would hold.
     *
     * ### Throughput is derived from what households eat, not authored per trade
     *
     * Only the last good in a chain has a demand of its own; everything above it exists to feed that. So
     * bread's throughput is what the residents eat, flour's is what the bakery needs to bake it, and
     * grain's is what the mill needs to grind that - walking the graph backwards from consumption.
     *
     * Doing it the other way round, by authoring a rate per baker, means the roster and the runtime each
     * carry their own idea of how much a town needs and they drift the first time either is retuned.
     * This way the chain balances by construction, which is also what makes a deviation of zero an exact
     * fixed point of [EconomyStep].
     *
     * @param traffic road traffic through the settlement, as the generator recorded it
     */
    fun of(
      catalogue: EconomyCatalogue,
      settlement: Int,
      population: Int,
      wealth: Double,
      traffic: Double,
      treasury: Double = PerResidentTreasury.DEFAULT.treasuryFor(population, wealth),
    ): SettlementReference {
      val throughput = HashMap<String, Double>()

      // Backwards through the graph: a good's own demand plus whatever the trades above it draw off. The
      // topological order guarantees a good's consumers are all settled before it is asked for.
      for (commodity in catalogue.topological.asReversed()) {
        val eaten = population * commodity.perCapitaPerDay
        val drawn = catalogue.trades().sumOf { trade ->
          val perUnit = trade.consumes.firstOrNull { it.commodity == commodity.id }?.perUnit ?: 0.0
          perUnit * (throughput[trade.produces] ?: 0.0)
        }
        throughput[commodity.id] = eaten + drawn
      }

      val stock = throughput.mapValues { (id, rate) -> rate * catalogue.commodityOrThrow(id).coverDays }

      return SettlementReference(
        settlement = settlement,
        population = population,
        throughput = throughput,
        stock = stock,
        priceMultiplier = priceMultiplierFor(wealth),
        kappa = kappaFor(traffic),
        treasury = treasury,
      )
    }

    /**
     * A wealthy town charges more, and a poor one less.
     *
     * Sub-linear in wealth so the band stays inside I1's half-to-four times with room for the deviation
     * to move inside it - a site multiplier that could already double the price would leave a shortage
     * nothing to say.
     */
    private fun priceMultiplierFor(wealth: Double): Double {
      return (0.5 + wealth).coerceIn(0.6, 1.6).pow(0.5)
    }

    /**
     * How fast the un-simulated world outside the map fills a shortage.
     *
     * Trade as a decay rate rather than as caravans. There is no bilateral matching and no pass over
     * pairs of settlements; a crossroads town refills in about two and a half game-days and an isolated
     * hamlet takes ten, which is the difference players actually notice. The upgrade path is clean:
     * make it pull toward a road-weighted regional mean rather than toward zero, and this is the special
     * case of that.
     */
    private fun kappaFor(traffic: Double): Double {
      return EconomyStep.MIN_TRADE_RATE + TRADE_RATE_PER_TRAFFIC * traffic
    }

    private const val TRADE_RATE_PER_TRAFFIC = 0.09

  }
}
