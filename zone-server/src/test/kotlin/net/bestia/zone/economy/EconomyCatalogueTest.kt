package net.bestia.zone.economy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The shipped catalogue, and the three shapes it must never take.
 *
 * Each refusal is checked against a file that actually has the defect. A boot check nothing has ever
 * seen fail is a boot check that might not work, and I13 in particular is the difference between a town
 * that recovers and one that is permanently dead.
 */
class EconomyCatalogueTest {

  private val catalogue = EconomyCatalogue().apply { load() }

  @Test
  fun `the chain runs farm to mill to bakery, and in that order`() {
    val order = catalogue.topological.map { it.id }

    assertEquals(listOf("grain", "flour", "bread"), order, "a stage out of order costs a shock a day to travel")
  }

  @Test
  fun `only bread is eaten, and everything above it exists to make it`() {
    // The property the whole reference derivation rests on: exactly one good has a demand of its own,
    // and every throughput above it is worked out from that rather than authored.
    val eaten = catalogue.commodities().filter { it.perCapitaPerDay > 0.0 }.map { it.id }

    assertEquals(listOf("bread"), eaten)
  }

  @Test
  fun `I13 - a cycle is refused, and the failure names the goods in it`() {
    val error = assertFailsWith<IllegalArgumentException> {
      EconomyCatalogue().load("economy/cyclic.yml")
    }

    assertTrue(error.message!!.contains("cycle"), "the message does not say what is wrong: ${error.message}")
    assertTrue(error.message!!.contains("grain"), "the message does not name the goods stuck in it")
  }

  @Test
  fun `two trades cannot make the same good`() {
    val error = assertFailsWith<IllegalArgumentException> {
      EconomyCatalogue().load("economy/two-producers.yml")
    }

    assertTrue(error.message!!.contains("bread"), "the message does not name the good: ${error.message}")
  }

  @Test
  fun `a trade must name a business or a sector, and only one`() {
    assertFailsWith<IllegalArgumentException> {
      Trade("nowhere", business = null, sector = null, building = FARM, produces = "grain", consumes = emptyList())
    }
  }

  private companion object {
    val FARM = net.bestia.worldgen.civ.BuildingFunction.FARM
  }
}
