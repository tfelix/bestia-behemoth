package net.bestia.worldgen.history

import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.vector.Vec2d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The simulation on hand-made settlement sites, without building a world. [HistoryStageTest] covers the
 * simulation against a real world; this covers rules that need a specific map to show.
 */
class HistorySimTest {

  @Test
  fun `cities of different cultures each found a civ`() {
    val chronicle = simulate(
      listOf(
        site(0.0, 0.0, SettlementTier.CITY, Culture.AGRARIAN),
        site(300.0, 0.0, SettlementTier.CITY, Culture.PASTORAL),
        site(0.0, 300.0, SettlementTier.CITY, Culture.HIGHLAND)
      )
    )

    assertEquals(3, chronicle.civs.size)
  }

  @Test
  fun `two neighbouring peoples go to war`() {
    val chronicle = simulate(twoNeighbouringPeoples())

    assertEquals(2, chronicle.civs.size)
    assertTrue(
      chronicle.events.any { it.kind == EventKind.WAR_DECLARED },
      "two warlike neighbours 40 km apart never went to war in ${chronicle.span}"
    )
  }

  @Test
  fun `the same sites give the same history`() {
    val first = simulate(twoNeighbouringPeoples())
    val second = simulate(twoNeighbouringPeoples())

    assertEquals(first.events.map { it.toString() }, second.events.map { it.toString() })
  }

  /** Two decisions on one salt read the same roll whenever their keys line up, which correlates them silently. */
  @Test
  fun `no two decisions share a salt`() {
    val salts = HistorySim::class.java.declaredFields
      .filter { it.name.endsWith("_SALT") }
      .associate { field ->
        field.isAccessible = true
        field.name to field.getLong(null)
      }

    val shared = salts.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }
    assertTrue(salts.isNotEmpty(), "found no salts to check")
    assertTrue(shared.isEmpty(), "salts used by more than one decision: $shared")
  }

  private fun twoNeighbouringPeoples(): List<SiteFacts> {
    return listOf(
      site(0.0, 0.0, SettlementTier.CITY, Culture.AGRARIAN),
      site(10.0, 0.0, SettlementTier.VILLAGE, Culture.AGRARIAN),
      site(0.0, 10.0, SettlementTier.VILLAGE, Culture.AGRARIAN),
      site(-10.0, 0.0, SettlementTier.VILLAGE, Culture.AGRARIAN),
      site(0.0, -10.0, SettlementTier.VILLAGE, Culture.AGRARIAN),
      site(40.0, 0.0, SettlementTier.CITY, Culture.PASTORAL),
      site(50.0, 0.0, SettlementTier.VILLAGE, Culture.PASTORAL),
      site(40.0, 10.0, SettlementTier.VILLAGE, Culture.PASTORAL),
      site(40.0, -10.0, SettlementTier.VILLAGE, Culture.PASTORAL)
    )
  }

  private fun simulate(sites: List<SiteFacts>): Chronicle {
    val facts = sites.mapIndexed { index, site -> site.withIndex(index) }
    return HistorySim(HistoryParams(), facts, emptyList(), SpecialSiteCandidates.EMPTY, 1L, 1L).run()
  }

  private fun site(xKm: Double, yKm: Double, tier: SettlementTier, culture: Culture): SiteFacts {
    return SiteFacts(
      index = 0,
      position = Vec2d(xKm * 1000.0, yKm * 1000.0),
      tier = tier,
      cultureIndex = Culture.indexOf(culture),
      potential = tier.maxPopulation / 2,
      habitability = 0.8,
      fertility = 0.7,
      volcanism = 0.0,
      floodRisk = 0.0,
      coastal = false,
      resourceValue = 0.5
    )
  }

  private fun SiteFacts.withIndex(index: Int): SiteFacts {
    return SiteFacts(
      index, position, tier, cultureIndex, potential, habitability, fertility, volcanism, floodRisk, coastal,
      resourceValue, mana
    )
  }
}
