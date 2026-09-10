package net.bestia.zone.ai.perception

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.economy.EconomyCatalogue
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.SettlementMarket
import net.bestia.zone.world.settlement.SettlementSiteIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Whether a workshop can work today.
 *
 * The stock-not-offer choice is the part worth pinning, and it is the opposite of the one
 * [SettlementFoodStalls] documents: the reserve a town holds back from sale is exactly what its own mill
 * draws on, so reading the offer would idle a workshop standing next to a full store.
 */
class SettlementWorkshopsTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val economy = mockk<SettlementEconomyService>()
  private val market = mockk<SettlementMarket>()
  private val sites = mockk<SettlementSiteIndex>()

  private val sut = SettlementWorkshops(catalogue, economy, sites)

  init {
    every { economy.marketOf(SETTLEMENT) } returns market
  }

  @Test
  fun `a business the economy models has a trade`() {
    assertEquals("flour", assertNotNull(sut.tradeOf("miller")).produces)
  }

  @Test
  fun `and one it does not, has none`() {
    // Most of them: twenty-six of the catalogue's businesses keep a shop the ledger never models.
    assertNull(sut.tradeOf("temple"))
  }

  @Test
  fun `somebody with no business at all has no trade`() {
    assertNull(sut.tradeOf(null))
  }

  @Test
  fun `a mill with grain can work`() {
    every { market.stockOf("grain") } returns 500.0

    assertTrue(sut.canSupply(SETTLEMENT, assertNotNull(sut.tradeOf("miller"))))
  }

  @Test
  fun `and one with an empty granary cannot`() {
    every { market.stockOf("grain") } returns 0.0

    assertFalse(sut.canSupply(SETTLEMENT, assertNotNull(sut.tradeOf("miller"))))
  }

  @Test
  fun `a sack short of one turn of the wheel is empty enough`() {
    // Against the recipe rather than against zero, so a mill with a handful of grain in the corner is
    // honestly idle rather than producing a sack out of a cupful.
    val milling = assertNotNull(sut.tradeOf("miller"))
    every { market.stockOf("grain") } returns milling.consumes.single().perUnit * 0.5

    assertFalse(sut.canSupply(SETTLEMENT, milling))
  }

  @Test
  fun `a farm consumes nothing and is never held up`() {
    val farming = assertNotNull(catalogue.trades().first { it.consumes.isEmpty() })

    assertTrue(sut.canSupply(SETTLEMENT, farming))
  }

  @Test
  fun `a settlement with no economy cannot supply anybody`() {
    every { economy.marketOf(SETTLEMENT + 1) } returns null

    assertFalse(sut.canSupply(SETTLEMENT + 1, assertNotNull(sut.tradeOf("miller"))))
  }

  private companion object {
    const val SETTLEMENT = 3
  }
}
