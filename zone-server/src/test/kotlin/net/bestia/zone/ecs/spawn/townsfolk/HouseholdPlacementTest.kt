package net.bestia.zone.ecs.spawn.townsfolk

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.Household
import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.worldgen.pop.PopulationSummary
import net.bestia.worldgen.pop.Sector
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ai.domain.townsfolk.OccupationCatalogue
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Where a household ends up, and what its people do when they get there.
 *
 * Everything here is derived rather than stored, so the assertion that matters most is that asking twice
 * gives the same answer - the whole scheme rests on a town being rebuilt identically after standing empty.
 * A hand-built settlement rather than a generated one, because what is under test is the assignment and
 * not the geometry: `SettlementSiteIndexTest` covers where the doors are.
 */
class HouseholdPlacementTest {

  private val temple = BusinessCatalogue.ALL.indexOfFirst { it.id == "temple" }
  private val potter = BusinessCatalogue.ALL.indexOfFirst { it.id == "potter" }

  private val site = SettlementSite(
    index = SETTLEMENT,
    centre = Vec2d(500.0, 500.0),
    tier = SettlementTier.entries.first(),
    population = summary(householdCount = 12, businesses = listOf(temple to 1, potter to 2)),
    unordered = listOf(
      building(propId = 30, BuildingFunction.RESIDENCE, at = 300.0),
      building(propId = 10, BuildingFunction.RESIDENCE, at = 100.0),
      building(propId = 20, BuildingFunction.RESIDENCE, at = 200.0),
      building(propId = 40, BuildingFunction.TEMPLE, at = 400.0, businessType = temple),
      building(propId = 50, BuildingFunction.CRAFT, at = 500.0, businessType = potter),
      building(propId = 60, BuildingFunction.CRAFT, at = 600.0, businessType = potter),
      building(propId = 70, BuildingFunction.FARM, at = 700.0),
      building(propId = 80, BuildingFunction.RESIDENCE, at = 800.0),
      building(propId = 90, BuildingFunction.RESIDENCE, at = 900.0),
      building(propId = 100, BuildingFunction.RESIDENCE, at = 1000.0),
    )
  )

  private val sites = mockk<SettlementSiteIndex>()
  private val occupations = OccupationCatalogue().apply { load() }

  private val sut: HouseholdPlacement

  init {
    every { sites.siteOf(SETTLEMENT) } returns site
    every { sites.siteOf(neq(SETTLEMENT)) } returns null
    // Metres to position units is the index's own job and `SettlementSiteIndexTest` covers it; here the
    // door coordinate passing through unchanged is what makes the assertions below readable.
    every { sites.doorstepOf(any()) } answers { firstArg<SettlementSite.Building>().door.let { Vec3L(it.x.toLong(), it.y.toLong(), 0) } }

    sut = HouseholdPlacement(sites, occupations)
  }

  @Test
  fun `houses are handed out in a fixed order, and run out`() {
    // Residence doors are at x=100..300 and 800..1000, and the site sorts by propId - which is ascending
    // with x - so the order is by door. That is what makes household 0 come home to the same house tomorrow.
    assertEquals(100L, homeX(household = 0))
    assertEquals(200L, homeX(household = 1))
    assertEquals(1000L, homeX(household = 5))
    assertNull(sut.of(SETTLEMENT, 6), "with six houses and twelve households, the seventh lives nowhere")
  }

  @Test
  fun `asking twice gives the same answer`() {
    val first = assertNotNull(sut.of(SETTLEMENT, 5))
    val second = assertNotNull(sut.of(SETTLEMENT, 5))

    assertEquals(first.home, second.home)
    assertEquals(first.workplace, second.workplace)
    assertEquals(first.household.members.size, second.household.members.size)
  }

  @Test
  fun `a trade household works in a building of its trade`() {
    // Household 0 keeps the temple and 1 and 2 the two potteries - the roster is laid out in catalogue
    // order, so which household holds which trade is a property of `Households.one`.
    assertEquals(400L, workX(household = 0), "the priest is not at the temple")
    assertEquals(600L, workX(household = 1), "the potters are not at a pottery")
    assertEquals(500L, workX(household = 2))
  }

  @Test
  fun `a farming household is sent to the barn`() {
    // Past the roster every household farms, and a farm building is the nearest thing to a field there is.
    assertEquals(700L, workX(household = 4))
  }

  @Test
  fun `a settlement with no houses puts nobody anywhere`() {
    val roofless = SettlementSite(
      index = SETTLEMENT,
      centre = Vec2d(500.0, 500.0),
      tier = SettlementTier.entries.first(),
      population = summary(householdCount = 4, businesses = emptyList()),
      unordered = listOf(building(propId = 40, BuildingFunction.TEMPLE, at = 400.0, businessType = temple))
    )
    every { sites.siteOf(SETTLEMENT) } returns roofless

    assertNull(sut.of(SETTLEMENT, 0))
  }

  @Test
  fun `a household that the settlement does not have is nobody`() {
    assertNull(sut.of(SETTLEMENT, 99), "household 99 of a twelve-household village does not exist")
    assertNull(sut.of(SETTLEMENT + 1, 0), "and neither does a settlement that is not there")
  }

  @Test
  fun `children are children whatever their household keeps`() {
    val potters = Household(1, potter, Sector.CRAFT, 0.4, listOf(Member(40, Kinship.HEAD), Member(8, Kinship.CHILD)))

    assertEquals("child", sut.occupationFor(potters, potters.members[1]).id)
  }

  @Test
  fun `a trade nobody keeps yet makes labourers rather than idlers`() {
    // Twenty-seven of the thirty trades have no occupation in this release. Their households have to do
    // something, and a town of people with nothing to do at all reads worse than one carrying things about.
    val potters = Household(1, potter, Sector.CRAFT, 0.4, listOf(Member(40, Kinship.HEAD)))

    assertEquals("labourer", sut.occupationFor(potters, potters.head).id)
  }

  @Test
  fun `a trade somebody does keep gets its own occupation`() {
    val clergy = Household(0, temple, Sector.CLERGY, 0.5, listOf(Member(50, Kinship.HEAD)))

    assertEquals("priest", sut.occupationFor(clergy, clergy.head).id)
  }

  @Test
  fun `a farming household farms`() {
    val farm = Household(9, -1, Sector.FARM, 0.2, listOf(Member(35, Kinship.HEAD), Member(20, Kinship.SERVANT)))

    assertEquals("farmer", sut.occupationFor(farm, farm.head).id)
    assertEquals("farmer", sut.occupationFor(farm, farm.members[1]).id, "a farm servant farms")
  }

  private fun homeX(household: Int): Long = assertNotNull(sut.of(SETTLEMENT, household)).home.x

  private fun workX(household: Int): Long =
    assertNotNull(assertNotNull(sut.of(SETTLEMENT, household)).workplace).x

  private fun building(
    propId: Long,
    function: BuildingFunction,
    at: Double,
    businessType: Int = SettlementSiteIndex.NO_BUSINESS
  ) = SettlementSite.Building(
    propId = propId,
    function = function,
    centre = Vec2d(at, 0.0),
    door = Vec2d(at, 0.0),
    floorElevation = 10.0,
    businessType = businessType
  )

  private fun summary(householdCount: Int, businesses: List<Pair<Int, Int>>) = PopulationSummary(
    settlement = SETTLEMENT,
    position = Vec2d(500.0, 500.0),
    population = householdCount * 4,
    wealth = 0.4,
    householdCount = householdCount,
    seed = 0xB0_0BL,
    businesses = businesses,
    sectors = intArrayOf(10, 10, 5, 3, 2, 2, 1)
  )

  private companion object {
    const val SETTLEMENT = 12
  }
}
