package net.bestia.zone.ai.perception

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.SettlementMarket
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where a townsperson goes for a meal, and what the town's own larder has to say about it.
 *
 * The fallback chain is the part worth pinning. Which trade a settlement has depends on its size - a
 * city has a market, a village a general store, a hamlet only ever the inn - so a lookup that named one
 * of them would feed cities and starve hamlets, and it would take a walk through a hamlet to notice.
 */
class SettlementFoodStallsTest {

  private val sites = mockk<SettlementSiteIndex>()
  private val economy = mockk<SettlementEconomyService>()
  private val market = mockk<SettlementMarket>()

  private val sut = SettlementFoodStalls(sites, economy)

  private val standingAt = Vec3L(0, 0, 0)

  init {
    every { sites.doorstepOf(any()) } answers {
      firstArg<SettlementSite.Building>().door.let { Vec3L(it.x.toLong(), it.y.toLong(), 0) }
    }
    every { economy.marketOf(SETTLEMENT) } returns market
    every { market.stockOf("bread") } returns 500.0
  }

  @Test
  fun `a city sends them to the market rather than to the inn next door`() {
    // Preference beats distance on purpose: the inn is closer, and a market is still where a townsperson
    // does their shopping.
    standingIn(site(business("inn", at = 5.0), business("market_trader", at = 90.0)))

    assertEquals(Vec3L(90, 0, 0), assertNotNull(sut.stallNear(standingAt)).doorstep)
  }

  @Test
  fun `a village with no market has a general store`() {
    standingIn(site(business("general_store", at = 40.0), business("baker", at = 10.0)))

    assertEquals(Vec3L(40, 0, 0), assertNotNull(sut.stallNear(standingAt)).doorstep)
  }

  @Test
  fun `and a hamlet has only ever the inn`() {
    // `BusinessCatalogue` gives the inn `alwaysAtLeastOne`, which is why almost nowhere comes back with
    // nothing at all.
    standingIn(site(business("inn", at = 12.0)))

    assertEquals(Vec3L(12, 0, 0), assertNotNull(sut.stallNear(standingAt)).doorstep)
  }

  @Test
  fun `the nearest of two of the same trade`() {
    standingIn(site(business("general_store", at = 70.0), business("general_store", at = 20.0)))

    assertEquals(Vec3L(20, 0, 0), assertNotNull(sut.stallNear(standingAt)).doorstep)
  }

  @Test
  fun `a settlement with nowhere to buy anything answers nothing`() {
    standingIn(site(business("temple", at = 30.0)))

    assertNull(sut.stallNear(standingAt))
  }

  @Test
  fun `out in the country there is no counter at all`() {
    every { sites.siteCovering(any(), any()) } returns null

    assertNull(sut.stallNear(standingAt))
  }

  @Test
  fun `an empty larder is a counter with nothing on it, not the absence of a counter`() {
    // The difference matters: no counter means walk away, and an empty one means the town is in
    // trouble. Collapsing the two would make a burnt field look like a hamlet.
    standingIn(site(business("inn", at = 12.0)))
    every { market.stockOf("bread") } returns 0.0

    val stall = assertNotNull(sut.stallNear(standingAt))

    assertTrue(!stall.inStock, "the town has no bread and the stall says it has")
    assertEquals(Vec3L(12, 0, 0), stall.doorstep)
  }

  private fun standingIn(site: SettlementSite) {
    every { sites.siteCovering(any(), any()) } returns site
  }

  private fun site(vararg buildings: SettlementSite.Building) = SettlementSite(
    index = SETTLEMENT,
    centre = Vec2d(0.0, 0.0),
    tier = SettlementTier.entries.first(),
    population = null,
    unordered = buildings.toList(),
  )

  private fun business(trade: String, at: Double) = SettlementSite.Building(
    propId = at.toLong(),
    function = BuildingFunction.CRAFT,
    centre = Vec2d(at, 0.0),
    door = Vec2d(at, 0.0),
    floorElevation = 0.0,
    businessType = BusinessCatalogue.ALL.indexOfFirst { it.id == trade },
  )

  private companion object {
    const val SETTLEMENT = 3
  }
}
