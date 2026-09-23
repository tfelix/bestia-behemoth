package net.bestia.zone.economy

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.pop.PopulationSummary
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.world.PersistedWorld
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A trade against a real ledger: what leaves the town, what reaches its strongbox, and what the next
 * customer is charged.
 *
 * The last of those is this branch's whole point. A shop whose price does not move when somebody clears
 * the shelf is a vending machine, and a player finds that out in about ten seconds.
 */
class ShopSettlementTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val sites = mockk<SettlementSiteIndex>()
  private val repository = mockk<SettlementLedgerRepository>(relaxed = true)
  private val worldService = mockk<WorldService>()
  private val clock = mockk<BestiaClock>()

  private var now = BestiaDateTime(year = 1, month = 2, day = 3, hour = 12, minute = 0, second = 0)

  private val service = SettlementEconomyService(
    catalogue = catalogue,
    step = EconomyStep(catalogue, UndamagedCapacity(), UnclaimedProduction()),
    damage = UndamagedCapacity(),
    sites = sites,
    money = PerResidentTreasury(),
    repository = repository,
    asyncJobExecutor = mockk<AsyncJobExecutor>().also {
      every { it.submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    },
    worldService = worldService,
    clock = clock,
  )

  init {
    every { sites.siteOf(VILLAGE) } returns site()
    every { sites.siteOf(neq(VILLAGE)) } returns null
    every { sites.siteCovering(any(), any()) } returns site()
    every { worldService.record } returns mockk<PersistedWorld>().also {
      every { it.shapeVersion } returns 1L
      every { it.pipelineVersion } returns 1L
    }
    every { clock.now() } answers { now }
    every { repository.findAll() } returns emptyList()
    every { repository.save(any()) } answers { firstArg() }
  }

  @Test
  fun `a player buys bread, and the next loaf costs a hair more`() {
    val before = quoteFor(1)

    buy(units = 200)

    assertTrue(
      quoteFor(1) > before,
      "two hundred loaves left the town and the next one still costs $before"
    )
  }

  @Test
  fun `I6 - what the player carries out has left the town`() {
    val standing = assertNotNull(shop()).let { market().stockOf("bread") }

    buy(units = 200)

    assertEquals(
      standing - 200.0,
      market().stockOf("bread"),
      absoluteTolerance = 1.0,
      message = "the shelves did not lose exactly what was carried out of them"
    )
  }

  @Test
  fun `I7 - the coins paid are the coins the treasury gains, to the penny`() {
    val reference = assertNotNull(service.referenceOf(VILLAGE))
    val quote = assertNotNull(shop()).quoteBuy(catalogue.commodityOrThrow("bread"), 200)

    service.settle(VILLAGE, "bread", quote.units, quote.coins, selling = false)

    assertEquals(
      reference.treasury + quote.coins,
      treasury(),
      absoluteTolerance = 1e-6,
      message = "the treasury and the till disagree, which is a leak in one direction or the other"
    )
  }

  @Test
  fun `selling into the town takes coin back out of it`() {
    val reference = assertNotNull(service.referenceOf(VILLAGE))
    val quote = assertNotNull(shop()).quoteSell(catalogue.commodityOrThrow("bread"), 50)

    service.settle(VILLAGE, "bread", quote.units, quote.coins, selling = true)

    assertEquals(reference.treasury - quote.coins, treasury(), absoluteTolerance = 1e-6)
    assertTrue(market().stockOf("bread") > reference.stock.getValue("bread"), "the loaves did not arrive")
  }

  @Test
  fun `a trade is what first gives a settlement a row`() {
    assertEquals(0, service.trackedSettlements, "the town is tracked before anybody has touched it")

    buy(units = 200)

    assertEquals(1, service.trackedSettlements, "a trade left no trace, so nothing would survive a restart")
  }

  @Test
  fun `and the town forgets it again once the shelves have refilled`() {
    buy(units = 200)

    // Six months, because the treasury is the slowest thing to come back: the shelves refill in weeks,
    // and the coin a purchase left behind reverts on a two-month time constant from thousands.
    repeat(6) {
      now = if (now.month == BestiaDateTime.MONTHS_PER_YEAR) {
        now.copy(year = now.year + 1, month = 1)
      } else {
        now.copy(month = now.month + 1)
      }
      service.catchUpAll()
    }

    assertEquals(0, service.trackedSettlements, "one purchase left a row behind for good")
  }

  @Test
  fun `buying the whole offer twice over is refused the second time`() {
    val shop = assertNotNull(shop())
    val bread = catalogue.commodityOrThrow("bread")
    val offered = shop.quoteBuy(bread, 1).let { market().offerableOf("bread").toInt() }

    val first = shop.quoteBuy(bread, offered)
    assertTrue(first.allowed, "the whole offer has to be buyable once")
    service.settle(VILLAGE, "bread", first.units, first.coins, selling = false)

    assertEquals(
      Shop.Refusal.OUT_OF_STOCK,
      assertNotNull(shop()).quoteBuy(bread, offered).refusal,
      "the shelf was cleared and the next customer was served anyway"
    )
  }

  private fun buy(units: Int) {
    val quote = assertNotNull(shop()).quoteBuy(catalogue.commodityOrThrow("bread"), units)
    assertTrue(quote.allowed, "the town refused a purchase this test needs to go through")

    service.settle(VILLAGE, "bread", quote.units, quote.coins, selling = false)
  }

  private fun quoteFor(units: Int): Long {
    return assertNotNull(shop()).quoteBuy(catalogue.commodityOrThrow("bread"), units).coins
  }

  private fun shop(): Shop? {
    return service.shopAt(0, 0)?.second
  }

  private fun market(): SettlementMarket {
    return assertNotNull(service.marketOf(VILLAGE))
  }

  /** Read back off the market, because the ledger's own state is deliberately not exposed. */
  private fun treasury(): Double {
    return market().treasuryWith(0)
  }

  private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double, message: String) {
    assertTrue(abs(expected - actual) <= absoluteTolerance, "$message (expected $expected, was $actual)")
  }

  private fun site() = SettlementSite(
    index = VILLAGE,
    centre = Vec2d(0.0, 0.0),
    tier = SettlementTier.entries.first(),
    population = PopulationSummary(
      settlement = VILLAGE,
      position = Vec2d(0.0, 0.0),
      population = 300,
      wealth = 0.4,
      householdCount = 75,
      seed = 1L,
      businesses = emptyList(),
      sectors = IntArray(7),
      traffic = 1.5,
    ),
    unordered = emptyList(),
  )

  private companion object {
    const val VILLAGE = 12
  }
}
