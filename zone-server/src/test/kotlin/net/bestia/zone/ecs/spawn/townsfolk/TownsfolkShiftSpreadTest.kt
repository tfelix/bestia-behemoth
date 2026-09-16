package net.bestia.zone.ecs.spawn.townsfolk

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.pop.PopulationSummary
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ai.domain.townsfolk.OccupationCatalogue
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That a town does not clock off all at once.
 *
 * Labourers and beggars both end their shift at 17:00 and every agent reads the same clock, so the whole
 * town used to flip to its evening inside one perception sweep and converge on one tile of the square. A
 * few minutes either way is the whole fix, and this is the test that would notice it going away.
 */
class TownsfolkShiftSpreadTest {

  private val sites = mockk<SettlementSiteIndex>()
  private val occupations = OccupationCatalogue().apply { load() }

  private val sut = TownsfolkRoster(sites, HouseholdPlacement(sites, occupations, TownsfolkResidencyConfig()))

  init {
    every { sites.siteOf(SETTLEMENT) } returns site()
    every { sites.doorstepOf(any()) } answers {
      firstArg<SettlementSite.Building>().door.let { Vec3L(it.x.toLong(), it.y.toLong(), 0) }
    }
  }

  @Test
  fun `a town's people do not all clock off in the same minute`() {
    val offsets = sut.of(SETTLEMENT).map { it.dayOffsetMinutes }.toSet()

    assertTrue(offsets.size > 1, "everybody in the town keeps the same minutes: $offsets")
  }

  @Test
  fun `and none of them drifts further than the town allows`() {
    val allowed = -occupations.dayJitterMinutes()..occupations.dayJitterMinutes()

    for (resident in sut.of(SETTLEMENT)) {
      assertTrue(
        resident.dayOffsetMinutes in allowed,
        "${TownsfolkIdentity.describe(resident.identity)} is ${resident.dayOffsetMinutes} minutes out"
      )
    }
  }

  @Test
  fun `a person keeps the same minutes across a rebuild`() {
    // The town is rebuilt from its seed whenever somebody comes near, and walking through a door destroys
    // and rebuilds one person. Either would hand out a fresh timetable if this were not derived.
    val before = sut.of(SETTLEMENT).associate { it.identity to it.dayOffsetMinutes }
    sut.forget(SETTLEMENT)
    val after = sut.of(SETTLEMENT).associate { it.identity to it.dayOffsetMinutes }

    assertTrue(before == after, "a rebuilt town re-rolled somebody's day")
  }

  private fun site(): SettlementSite {
    val houses = (0 until HOUSES).map { index ->
      SettlementSite.Building(
        propId = index.toLong(),
        function = BuildingFunction.RESIDENCE,
        centre = Vec2d(index * 10.0, 0.0),
        door = Vec2d(index * 10.0, 0.0),
        floorElevation = 10.0,
        businessType = SettlementSiteIndex.NO_BUSINESS,
      )
    }

    return SettlementSite(
      index = SETTLEMENT,
      centre = Vec2d(0.0, 0.0),
      tier = SettlementTier.entries.first(),
      population = PopulationSummary(
        settlement = SETTLEMENT,
        position = Vec2d(0.0, 0.0),
        population = HOUSES * 4,
        wealth = 0.4,
        householdCount = HOUSES,
        seed = 0xB0_0BL,
        businesses = emptyList(),
        sectors = intArrayOf(10, 10, 5, 3, 2, 2, 1),
      ),
      unordered = houses,
    )
  }

  private companion object {
    const val SETTLEMENT = 4
    const val HOUSES = 20
  }
}
