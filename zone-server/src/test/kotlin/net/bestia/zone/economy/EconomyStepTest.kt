package net.bestia.zone.economy

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The behaviour invariants: that an untouched town stays where it is, a ruined one comes back, and
 * neither the shelves nor the prices can leave their bounds on the way.
 *
 * I17 is the one that matters most - it is the no-dead-world proof, and without it every other
 * guarantee here is satisfied by an economy that simply stops.
 */
class EconomyStepTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val step = EconomyStep(catalogue, UndamagedCapacity(), UnclaimedProduction())

  private val village = reference(population = 300)

  @Test
  fun `I16 - a thousand undisturbed days leave a settlement where it started`() {
    val rested = walk(LedgerState(lastStepDay = START), days = 1_000)

    for (commodity in catalogue.commodities()) {
      val drift = rested.deltaStock[commodity.id] ?: 0.0
      assertTrue(
        abs(drift) < village.stock.getValue(commodity.id) * 1e-6,
        "${commodity.id} drifted $drift with nothing happening to it, so the reference is not a fixed point"
      )
      assertTrue(
        abs(rested.deltaLogPrice[commodity.id] ?: 0.0) < 1e-6,
        "${commodity.id}'s price drifted with nothing happening to it"
      )
    }
  }

  @Test
  fun `I18 - a town nobody trades with never earns a row`() {
    // Structural, not incidental: the step is what a player standing in a town runs, and if it moved the
    // books at all then merely being looked at would cost a database write.
    val looked = walk(LedgerState(treasury = village.treasury, lastStepDay = START), days = 90)

    assertTrue(
      looked.isNegligible(stockTolerance = 1e-3, priceTolerance = 1e-6, treasuryReference = village.treasury),
      "standing in the town for ninety days moved its books to $looked"
    )
  }

  @Test
  fun `I17 - a village driven empty comes back to within five percent in sixty days`() {
    val emptied = LedgerState(
      deltaStock = village.stock.mapValues { (_, reference) -> -reference },
      treasury = 0.0,
      lastStepDay = START,
    )

    val recovered = walk(emptied, days = 60)

    for (commodity in catalogue.commodities()) {
      val expected = village.stock.getValue(commodity.id)
      val short = -(recovered.deltaStock[commodity.id] ?: 0.0)
      assertTrue(
        short < expected * 0.05,
        "${commodity.id} is still $short short of $expected after sixty days, so the world can be killed"
      )
    }
  }

  @Test
  fun `I3 and I4 - the shelves stay between empty and full whatever the books say`() {
    val absurd = LedgerState(
      deltaStock = village.stock.mapValues { (_, reference) -> reference * 500.0 },
      lastStepDay = START,
    )

    val market = market(walk(absurd, days = 5))

    for (commodity in catalogue.commodities()) {
      val standing = market.stockOf(commodity.id)
      assertTrue(standing >= 0.0, "${commodity.id} is at $standing, which is less than nothing")
      assertTrue(
        standing <= village.warehouseCap(commodity.id) + 1e-9,
        "${commodity.id} is at $standing, over the ${village.warehouseCap(commodity.id)} the town can hold"
      )
    }
  }

  @Test
  fun `I1 - the price stays inside half to four times the reference`() {
    // Both ends, from the two states that would break it: nothing on the shelves, and far too much.
    val starved = walk(LedgerState(deltaStock = village.stock.mapValues { (_, s) -> -s }, lastStepDay = START), 40)
    val glutted = walk(LedgerState(deltaStock = village.stock.mapValues { (_, s) -> s * 20 }, lastStepDay = START), 40)

    for (state in listOf(starved, glutted)) {
      val market = market(state)
      for (commodity in catalogue.commodities()) {
        val price = market.priceOf(commodity.id)
        val reference = commodity.refPrice * village.priceMultiplier
        assertTrue(
          price >= reference * PriceCurve.FLOOR * 0.999,
          "${commodity.id} fell to $price against a reference of $reference"
        )
        assertTrue(
          price <= reference * PriceCurve.CEILING * 1.001,
          "${commodity.id} rose to $price against a reference of $reference"
        )
      }
    }
  }

  @Test
  fun `burn the field and the loaf runs short, with nothing anywhere saying so`() {
    // The chain, and the whole reason the production function is a Leontief minimum. Nothing here knows
    // that bread is made of flour or flour of grain except `economy.yml`.
    val burnt = EconomyStep(catalogue, { _, trade -> if (trade == "farm") 0.2 else 1.0 }, UnclaimedProduction())

    var state = LedgerState(lastStepDay = START)
    repeat(20) { state = burnt.advance(village, state, state.lastStepDay + 1.0) }

    val market = market(state)
    assertTrue(
      market.stockOf("bread") < village.stock.getValue("bread"),
      "the field burnt and the bakery did not notice"
    )
    assertTrue(
      market.priceOf("bread") > catalogue.commodityOrThrow("bread").refPrice * village.priceMultiplier,
      "bread got scarce and did not get dearer"
    )
  }

  @Test
  fun `I19 - more damage is never better`() {
    val outcomes = listOf(1.0, 0.8, 0.5, 0.2, 0.0).map { share ->
      val hurt = EconomyStep(catalogue, { _, trade -> if (trade == "farm") share else 1.0 }, UnclaimedProduction())
      var state = LedgerState(lastStepDay = START)
      repeat(25) { state = hurt.advance(village, state, state.lastStepDay + 1.0) }
      market(state).stockOf("bread")
    }

    assertEquals(outcomes.sortedDescending(), outcomes, "burning more of the field left the town better fed")
  }

  @Test
  fun `I5 - every commodity drains, however imperishable`() {
    // Not a boot check, because it is not a property of a commodity: what drains a glut is spoilage
    // *and* trade with the outside world, and the second is never zero. This is what would fail if
    // somebody set the trade floor to nothing, which is the way the invariant could actually be lost.
    val glutted = LedgerState(deltaStock = village.stock.mapValues { (_, s) -> s * 10 }, lastStepDay = START)

    val settled = walk(glutted, days = 200)

    for (commodity in catalogue.commodities()) {
      val left = settled.deltaStock.getValue(commodity.id)
      assertTrue(
        left < village.stock.getValue(commodity.id) * 0.01,
        "${commodity.id} still has $left piled up after two hundred days, so nothing drains it"
      )
    }
  }

  @Test
  fun `I12 - what the locals keep back is not on offer`() {
    val market = market(LedgerState(lastStepDay = START))
    val bread = market.stockOf("bread")

    assertTrue(market.offerableOf("bread") < bread, "the whole town's bread is for sale, so it can be bought out")
    assertTrue(market.offerableOf("bread") >= 0.0, "a town short of bread offers a negative amount of it")
  }

  private fun walk(from: LedgerState, days: Int): LedgerState {
    return (1..days).fold(from) { state, _ -> step.advance(village, state, state.lastStepDay + 1.0) }
  }

  private fun market(state: LedgerState): SettlementMarket {
    return SettlementMarket(catalogue, village, state, state.lastStepDay.mod(Commodity.DAYS_PER_YEAR.toDouble()))
  }

  private fun reference(population: Int): SettlementReference {
    return SettlementReference.of(catalogue, settlement = 7, population = population, wealth = 0.4, traffic = 1.5)
  }

  private companion object {
    const val START = 412.0
  }
}
