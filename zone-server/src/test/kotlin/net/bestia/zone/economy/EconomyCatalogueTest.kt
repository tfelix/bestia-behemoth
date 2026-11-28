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
    val order = catalogue.topological.map { it.id }.filter { it in setOf("grain", "flour", "bread") }

    assertEquals(listOf("grain", "flour", "bread"), order, "a stage out of order costs a shock a day to travel")
  }

  @Test
  fun `a good residents use can also feed a trade, and its throughput counts both`() {
    val reference = SettlementReference.of(catalogue, settlement = 1, population = 1000, wealth = 1.0, traffic = 0.0)
    val charcoal = catalogue.commodityOrThrow("charcoal")
    val forge = catalogue.trades().single { it.id == "smithing" }.consumes.single { it.commodity == "charcoal" }

    val households = 1000 * charcoal.perCapitaPerDay
    val smith = reference.throughput.getValue("nails") * forge.perUnit

    assertEquals(households + smith, reference.throughput.getValue("charcoal"), 1e-9)
  }

  @Test
  fun `a log is cut before it is sawn or charred`() {
    val order = catalogue.topological.map { it.id }

    assertTrue(order.indexOf("log") < order.indexOf("plank"), "plank comes before its log in $order")
    assertTrue(order.indexOf("log") < order.indexOf("charcoal"), "charcoal comes before its log in $order")
  }

  @Test
  fun `only the woodcutter works the woods, and only the farm the fields`() {
    val byLand = catalogue.trades().filter { it.land != null }.associate { it.id to it.land }

    assertEquals(mapOf("farm" to Trade.Land.ARABLE, "woodcutting" to Trade.Land.WOODLAND), byLand)
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
