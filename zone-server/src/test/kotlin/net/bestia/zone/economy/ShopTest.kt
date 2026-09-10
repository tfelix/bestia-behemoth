package net.bestia.zone.economy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a town will trade, at what price, and the two things it refuses.
 *
 * The round-trip tests are the ones that matter. A shop where buying and selling back is not a loss is
 * an income, and it is an income that scales with how fast a player can click.
 */
class ShopTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val bread = catalogue.commodityOrThrow("bread")

  private val village = SettlementReference.of(catalogue, settlement = 7, population = 300, wealth = 0.4, traffic = 1.5)

  @Test
  fun `I10 - buying a loaf and selling it straight back loses money`() {
    val shop = shop()

    assertTrue(
      shop.quoteSell(bread, 1).coins < shop.quoteBuy(bread, 1).coins,
      "a round trip in one town pays, which is an income rather than a trade"
    )
  }

  @Test
  fun `I11 - and so does doing it in bulk, at every size`() {
    val shop = shop()

    for (units in listOf(1, 5, 20, 100, 300)) {
      val bought = shop.quoteBuy(bread, units)
      val sold = shop.quoteSell(bread, units)
      if (!bought.allowed) continue

      assertTrue(
        sold.coins < bought.coins,
        "$units loaves cost ${bought.coins} and sell back for ${sold.coins}, which is an income"
      )
    }
  }

  @Test
  fun `buying out the shelf costs more per loaf than buying one`() {
    // The point of settling unit by unit. Priced off one snapshot the profit on an arbitrage would be
    // linear in how much a player can carry; priced against a town with one fewer loaf each time, the
    // marginal price climbs to meet the other town's and the trade extinguishes itself.
    val shop = shop()
    val single = shop.quoteBuy(bread, 1).coins
    val bulk = shop.quoteBuy(bread, 300)

    assertTrue(bulk.allowed, "three hundred is over the offer, so this measures a refusal instead")
    assertTrue(
      bulk.coins > single * 300,
      "three hundred cost ${bulk.coins} against ${single * 300} at the single-loaf price"
    )
  }

  @Test
  fun `an item the town does not deal in is refused rather than priced at nothing`() {
    val quote = shop().quoteBuy(commodity = null, units = 1)

    assertEquals(Shop.Refusal.NOT_STOCKED, quote.refusal)
    assertEquals(0, quote.units)
  }

  @Test
  fun `I12 - more than is on offer is refused, even with plenty on the shelves`() {
    val shop = shop()
    val market = market(LedgerState(treasury = village.treasury, lastStepDay = DAY))
    val offered = market.offerableOf("bread").toInt()

    assertTrue(market.stockOf("bread") > offered, "nothing is being held back, so this proves nothing")
    assertEquals(Shop.Refusal.OUT_OF_STOCK, shop.quoteBuy(bread, offered + 1).refusal)
    assertEquals(null, shop.quoteBuy(bread, offered).refusal, "the surplus itself has to be buyable")
  }

  @Test
  fun `I8 - a town with an empty strongbox will not buy`() {
    val broke = shop(LedgerState(treasury = 0.0, lastStepDay = DAY))

    assertEquals(Shop.Refusal.TREASURY_EMPTY, broke.quoteSell(bread, 20).refusal)
  }

  @Test
  fun `I9 - and a town with a full one will not either`() {
    // The answer to dumping ten thousand apples: a refusal, not a collapsing price. A bad price would
    // still transfer money.
    val flush = shop(LedgerState(treasury = village.treasury * 10, lastStepDay = DAY))

    assertEquals(Shop.Refusal.TREASURY_FULL, flush.quoteBuy(bread, 20).refusal)
  }

  @Test
  fun `a poor village charges less for the same loaf than a rich town`() {
    val poor = SettlementReference.of(catalogue, 1, population = 300, wealth = 0.05, traffic = 0.0)
    val rich = SettlementReference.of(catalogue, 2, population = 300, wealth = 1.4, traffic = 4.0)

    assertTrue(
      priceIn(poor) < priceIn(rich),
      "wealth does not move the price, so there is nothing for a merchant to arbitrage"
    )
  }

  private fun priceIn(reference: SettlementReference): Long {
    val state = LedgerState(treasury = reference.treasury, lastStepDay = DAY)
    val market = SettlementMarket(catalogue, reference, state, DAY.mod(Commodity.DAYS_PER_YEAR.toDouble()))

    return Shop(catalogue, market, reference).quoteBuy(bread, 1).coins
  }

  private fun shop(state: LedgerState = LedgerState(treasury = village.treasury, lastStepDay = DAY)): Shop {
    return Shop(catalogue, market(state), village)
  }

  private fun market(state: LedgerState): SettlementMarket {
    return SettlementMarket(catalogue, village, state, DAY.mod(Commodity.DAYS_PER_YEAR.toDouble()))
  }

  private companion object {
    const val DAY = 412.0
  }
}
