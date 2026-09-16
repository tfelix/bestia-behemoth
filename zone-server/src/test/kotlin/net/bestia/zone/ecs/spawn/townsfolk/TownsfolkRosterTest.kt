package net.bestia.zone.ecs.spawn.townsfolk

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.pop.Household
import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.worldgen.pop.PopulationSummary
import net.bestia.worldgen.pop.Sector
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Who lives in a town, and the one question residency asks of each of them: where would you be right now.
 *
 * The anchor is the whole reason this type exists. It has to be answerable without building anybody -
 * that is what makes deciding cheap - so it is a pure function of an occupation and the clock, and these
 * are the four shapes it takes.
 */
class TownsfolkRosterTest {

  private val sites = mockk<SettlementSiteIndex>()
  private val placement = mockk<HouseholdPlacement>()

  private val farmer = Occupation("farmer", "farmer", null, HourWindow(6, 19), HourWindow(21, 5))
  private val child = Occupation("child", "child", null, null, HourWindow(21, 7))

  private val household = Household(
    index = 0,
    business = -1,
    sector = Sector.FARM,
    wealth = 0.3,
    members = listOf(Member(40, Kinship.HEAD), Member(9, Kinship.CHILD)),
  )

  private val sut: TownsfolkRoster

  init {
    every { sites.siteOf(SETTLEMENT) } returns site(households = 1)
    every { placement.of(SETTLEMENT, 0) } returns HouseholdPlacement.Placement(
      settlement = SETTLEMENT,
      household = household,
      residents = listOf(0, 1),
      home = HOME,
      homeBuilding = 11L,
      workplace = FIELD,
    )
    every { placement.occupationFor(any(), any()) } answers {
      if (secondArg<Member>().kinship == Kinship.CHILD) child else farmer
    }
    every { placement.dayOffsetOf(any(), any(), any()) } returns 0

    sut = TownsfolkRoster(sites, placement)
  }

  @Test
  fun `the people a household puts out of doors are on it`() {
    val roster = sut.of(SETTLEMENT)

    assertEquals(2, roster.size)
    assertEquals(
      listOf(
        TownsfolkIdentity.of(SETTLEMENT, 0, 0),
        TownsfolkIdentity.of(SETTLEMENT, 0, 1),
      ),
      roster.map { it.identity }
    )
  }

  @Test
  fun `a member the household keeps at home is not on it`() {
    every { placement.of(SETTLEMENT, 0) } returns HouseholdPlacement.Placement(
      settlement = SETTLEMENT,
      household = household,
      residents = listOf(0),
      home = HOME,
      homeBuilding = 11L,
      workplace = FIELD,
    )

    assertEquals(listOf(TownsfolkIdentity.of(SETTLEMENT, 0, 0)), sut.of(SETTLEMENT).map { it.identity })
  }

  @Test
  fun `on shift, somebody is at their post`() {
    assertEquals(FIELD, sut.of(SETTLEMENT).first().anchorAt(at(12)))
  }

  @Test
  fun `off shift, they are at home`() {
    val worker = sut.of(SETTLEMENT).first()

    assertEquals(HOME, worker.anchorAt(at(22)), "the field is shut at ten at night")
    assertEquals(HOME, worker.anchorAt(at(5)), "and before dawn")
  }

  @Test
  fun `somebody with no shift is always at home`() {
    val idler = sut.of(SETTLEMENT)[1]

    for (hour in 0 until 24) {
      assertEquals(HOME, idler.anchorAt(at(hour)), "a child was somewhere else at $hour:00")
    }
  }

  @Test
  fun `a worker with nowhere to work stays home too`() {
    // A town whose roster has no barn for its farmers. The shift is real and the place to keep it is not.
    every { placement.of(SETTLEMENT, 0) } returns HouseholdPlacement.Placement(
      settlement = SETTLEMENT,
      household = household,
      residents = listOf(0, 1),
      home = HOME,
      homeBuilding = 11L,
      workplace = null,
    )

    assertEquals(HOME, sut.of(SETTLEMENT).first().anchorAt(at(12)))
  }

  @Test
  fun `a town is expanded once and kept`() {
    // The point of caching: residency asks this four times a second while a player stands in the street,
    // and expanding a city's households on every pass would cost more than the entities it is avoiding.
    sut.of(SETTLEMENT)
    sut.of(SETTLEMENT)

    verify(exactly = 1) { placement.of(SETTLEMENT, 0) }
  }

  @Test
  fun `a settlement nobody lives in has nobody on it`() {
    every { sites.siteOf(EMPTY) } returns null

    assertEquals(emptyList(), sut.of(EMPTY))
  }

  private fun at(hour: Int): Int {
    return hour * HourWindow.MINUTES_PER_HOUR
  }

  private fun site(households: Int) = SettlementSite(
    index = SETTLEMENT,
    centre = Vec2d(1_000.0, 1_000.0),
    tier = SettlementTier.entries.first(),
    population = PopulationSummary(
      settlement = SETTLEMENT,
      position = Vec2d(1_000.0, 1_000.0),
      population = households * 4,
      wealth = 0.4,
      householdCount = households,
      seed = 42L,
      businesses = emptyList(),
      sectors = intArrayOf(4, 0, 0, 0, 0, 0, 0)
    ),
    unordered = emptyList()
  )

  private companion object {
    const val SETTLEMENT = 5
    const val EMPTY = 6

    val HOME = Vec3L(1_000, 1_000, 0)
    val FIELD = Vec3L(1_800, 1_000, 0)
  }
}
