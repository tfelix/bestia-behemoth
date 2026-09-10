package net.bestia.worldgen.pop

import net.bestia.worldgen.vector.Vec2d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The property [Households] is built around and nothing checked: one household can be expanded without
 * expanding any other, and comes out the same either way.
 *
 * It is what lets a zone server materialise the people in the one house a player walked up to, rather than
 * the four hundred households of the city around it. A single shared random stream would break it silently
 * - every household would still look plausible, and they would simply be different people depending on how
 * many had been asked for first.
 */
class HouseholdExpansionTest {

  private val summary = PopulationSummary(
    settlement = 7,
    position = Vec2d(1_000.0, 2_000.0),
    population = 1_600,
    wealth = 0.45,
    householdCount = 400,
    seed = 0x5EED_1234L,
    // A roster in catalogue order, as EconomyProbe reports it.
    businesses = listOf(0 to 2, 6 to 3, 12 to 1),
    sectors = intArrayOf(700, 400, 200, 120, 80, 60, 40)
  )

  @Test
  fun `a household expanded alone is the household expanded with the whole town`() {
    val all = Households.expand(summary)

    for (index in listOf(0, 1, 5, 399)) {
      val alone = Households.one(summary, index)
      val together = all[index]

      assertEquals(together.business, alone.business, "household $index took a different trade")
      assertEquals(together.sector, alone.sector)
      assertEquals(together.wealth, alone.wealth, "household $index came out with different money")
      assertEquals(
        together.members.map { it.kinship to it.age },
        alone.members.map { it.kinship to it.age },
        "household $index came out with different people"
      )
    }
  }

  @Test
  fun `the last household does not depend on the first having been asked for`() {
    // The sharpest form of the same claim, and the one a shared stream fails: nothing at all is expanded
    // before this call, and the answer still has to match the full expansion.
    val alone = Households.one(summary, summary.householdCount - 1)
    val together = Households.expand(summary).last()

    assertEquals(together.wealth, alone.wealth)
    assertEquals(together.members.size, alone.members.size)
  }

  @Test
  fun `the roster is laid out in order, and the rest farm`() {
    // What makes a household index a stable name for a person: adding people to a town appends farmers
    // rather than renumbering everybody who already lived there.
    assertEquals(0, Households.one(summary, 0).business, "the first trade takes the first households")
    assertEquals(0, Households.one(summary, 1).business)
    assertEquals(6, Households.one(summary, 2).business)
    assertEquals(12, Households.one(summary, 5).business)
    assertEquals(-1, Households.one(summary, 6).business, "past the roster, households farm")
    assertEquals(Sector.FARM, Households.one(summary, 399).sector)
  }

  @Test
  fun `a household has a head and somebody in it`() {
    for (household in Households.expand(summary)) {
      assertTrue(household.members.isNotEmpty(), "household ${household.index} is empty")
      assertEquals(Kinship.HEAD, household.head.kinship)
    }
  }
}
