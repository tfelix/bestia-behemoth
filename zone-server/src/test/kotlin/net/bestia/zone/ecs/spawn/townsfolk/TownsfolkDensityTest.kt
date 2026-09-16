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
 * How many people a town actually stands up, against how many front doors it has.
 *
 * The one invariant the rest of this package does not state anywhere: a street must not hold more people
 * than the houses on it could plausibly hold. It was violated by two separate factors at once - houses
 * were handed out with a modulo, so a residence took several households, and every member of each of
 * those households was then put outside. Either one alone is easy to reintroduce while the other stays
 * fixed, which is why this is asserted end to end through the real placement rather than per class.
 */
class TownsfolkDensityTest {

  private val sites = mockk<SettlementSiteIndex>()
  private val occupations = OccupationCatalogue().apply { load() }
  private val config = TownsfolkResidencyConfig()

  private val sut = TownsfolkRoster(sites, HouseholdPlacement(sites, occupations, config))

  init {
    every { sites.siteOf(SETTLEMENT) } returns site()
    every { sites.doorstepOf(any()) } answers {
      firstArg<SettlementSite.Building>().door.let { Vec3L(it.x.toLong(), it.y.toLong(), 0) }
    }
  }

  @Test
  fun `a town stands up no more people than its houses can hold`() {
    val roster = sut.of(SETTLEMENT)

    assertTrue(
      roster.size <= HOUSES * config.maxPerHome,
      "$HOUSES houses put ${roster.size} people on the street"
    )
    assertTrue(roster.size >= HOUSES * config.minPerHome, "only ${roster.size} people for $HOUSES houses")
  }

  @Test
  fun `everybody standing there has a door of their own`() {
    // Distinct homes, not distinct people: two of one household share a door, and nobody shares with
    // another household. That is what "one household to a house" means from the street.
    val doors = sut.of(SETTLEMENT).map { it.homeBuilding }.toSet()

    assertTrue(doors.size <= HOUSES, "${doors.size} doors for $HOUSES houses")
  }

  @Test
  fun `a town is not staffed entirely by its heads of household`() {
    // The draw beyond the head is uniform rather than by kinship rank, so that children keep turning up.
    // A rank order would seat the spouse every time and the child occupation would never be spoken to.
    val occupations = sut.of(SETTLEMENT).map { it.occupation.id }.toSet()

    assertTrue(occupations.contains("child"), "no child stands anywhere in a town of $HOUSES houses")
    assertTrue(occupations.any { it != "child" }, "the town is all children")
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
        population = HOUSES * 3 * 4,
        wealth = 0.4,
        // Three times the houses, which is about the ratio a generated town comes out at.
        householdCount = HOUSES * 3,
        seed = 0xB0_0BL,
        businesses = emptyList(),
        sectors = intArrayOf(10, 10, 5, 3, 2, 2, 1),
      ),
      unordered = houses,
    )
  }

  private companion object {
    const val SETTLEMENT = 7
    const val HOUSES = 40
  }
}
