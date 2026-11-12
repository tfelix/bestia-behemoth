package net.bestia.worldgen.history

import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.CivOrigin
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
  fun `a world with one city still has two peoples`() {
    val chronicle = simulate(
      listOf(
        site(0.0, 0.0, SettlementTier.CITY, Culture.AGRARIAN),
        site(10.0, 0.0, SettlementTier.VILLAGE, Culture.AGRARIAN),
        site(60.0, 0.0, SettlementTier.TOWN, Culture.AGRARIAN),
        site(5.0, 5.0, SettlementTier.HAMLET, Culture.AGRARIAN)
      )
    )

    assertTrue(chronicle.civs.size >= 2, "only ${chronicle.civs.size} civ")
    assertEquals(2, chronicle.civs[1].capital, "the second people should settle the town far from the city")
  }

  @Test
  fun `strangers can land on an empty coast`() {
    val sites = twoPeoplesAndAnEmptySite(emptySiteIsCoastal = true)
    val chronicle = simulate(sites, HistoryParams(arrivalChance = 50.0, arrivalFrom = 0, migrationChance = 0.0))

    val arrived = chronicle.civs.single { it.origin == CivOrigin.ARRIVED }
    assertEquals(sites.lastIndex, arrived.capital)
    assertTrue(chronicle.events.any { it.kind == EventKind.CIV_ARRIVED })
    assertTrue(
      chronicle.civs.none { it.origin == CivOrigin.MIGRATED },
      "migrants took a coastal site with migration switched off"
    )
  }

  @Test
  fun `a people can migrate overland to a remote inland site`() {
    val sites = twoPeoplesAndAnEmptySite(emptySiteIsCoastal = false)
    val chronicle = simulate(sites, HistoryParams(migrationChance = 50.0, migrationFrom = 0, arrivalChance = 0.0))

    val migrated = chronicle.civs.single { it.origin == CivOrigin.MIGRATED }
    assertEquals(sites.lastIndex, migrated.capital)
    assertTrue(chronicle.events.any { it.kind == EventKind.CIV_MIGRATED })
  }

  @Test
  fun `far towns of a large civ can break away`() {
    val sites = listOf(
      site(0.0, 0.0, SettlementTier.CITY, Culture.HIGHLAND),
      site(20.0, 0.0, SettlementTier.VILLAGE, Culture.HIGHLAND),
      site(40.0, 0.0, SettlementTier.VILLAGE, Culture.HIGHLAND),
      site(60.0, 0.0, SettlementTier.VILLAGE, Culture.HIGHLAND),
      site(80.0, 0.0, SettlementTier.VILLAGE, Culture.HIGHLAND),
      site(100.0, 0.0, SettlementTier.VILLAGE, Culture.HIGHLAND),
      site(0.0, 600.0, SettlementTier.CITY, Culture.AGRARIAN)
    )
    val params = HistoryParams(secessionMinTowns = 3, revoltRate = 1.0, revoltSuccess = 1.0)
    val chronicle = simulate(sites, params)

    val seceded = chronicle.civs.firstOrNull { it.origin == CivOrigin.SECEDED }
    assertTrue(seceded != null, "no town broke away: ${chronicle.events.filter { it.kind == EventKind.CIV_REVOLT }}")
    assertEquals(0, seceded.parent)

    val secession = chronicle.events.first { it.kind == EventKind.CIV_SECEDED }
    val cause = chronicle.events.first { it.id in secession.causes }
    assertEquals(EventKind.CIV_REVOLT, cause.kind)
  }

  @Test
  fun `survivors of a buried town can found a people of their own`() {
    val sites = listOf(
      site(0.0, 0.0, SettlementTier.CITY, Culture.HIGHLAND),
      site(10.0, 0.0, SettlementTier.TOWN, Culture.HIGHLAND),
      site(400.0, 0.0, SettlementTier.CITY, Culture.AGRARIAN),
      // Out of reach of the highland people's expansion, inside the survivors' reach.
      site(-90.0, 0.0, SettlementTier.VILLAGE, Culture.HIGHLAND)
    )
    val volcanoUnderTheTown = VentFacts(0, Vec2d(10_000.0, 0.0), strength = 1.0)
    val params = HistoryParams(
      refugeeChance = 1.0, refugeeMinPopulation = 0.0, refugeeRange = 200_000.0,
      arrivalChance = 0.0, migrationChance = 0.0
    )
    val chronicle = simulate(sites, params, vents = listOf(volcanoUnderTheTown))

    val refounded = chronicle.civs.firstOrNull { it.origin == CivOrigin.REFOUNDED }
    assertTrue(refounded != null, "nobody survived to found anything: ${chronicle.eventCensus()}")
    assertEquals(3, refounded.capital)

    val founding = chronicle.events.first { it.kind == EventKind.CIV_REFOUNDED }
    assertEquals(EventKind.SETTLEMENT_BURIED, chronicle.events.first { it.id in founding.causes }.kind)
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

  /** Two peoples far out of each other's reach, and one empty site farther still. */
  private fun twoPeoplesAndAnEmptySite(emptySiteIsCoastal: Boolean): List<SiteFacts> {
    return listOf(
      site(0.0, 0.0, SettlementTier.CITY, Culture.AGRARIAN),
      site(10.0, 0.0, SettlementTier.VILLAGE, Culture.AGRARIAN),
      site(400.0, 0.0, SettlementTier.CITY, Culture.HIGHLAND),
      site(400.0, 10.0, SettlementTier.VILLAGE, Culture.HIGHLAND),
      site(200.0, 300.0, SettlementTier.VILLAGE, Culture.AGRARIAN, coastal = emptySiteIsCoastal)
    )
  }

  private fun simulate(
    sites: List<SiteFacts>,
    params: HistoryParams = HistoryParams(),
    vents: List<VentFacts> = emptyList()
  ): Chronicle {
    val facts = sites.mapIndexed { index, site -> site.withIndex(index) }
    return HistorySim(params, facts, vents, SpecialSiteCandidates.EMPTY, 1L, 1L).run()
  }

  private fun site(
    xKm: Double,
    yKm: Double,
    tier: SettlementTier,
    culture: Culture,
    coastal: Boolean = false
  ): SiteFacts {
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
      coastal = coastal,
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
