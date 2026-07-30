package net.bestia.worldgen.pop

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The economy, the business catalogue's decision procedure, and the household expansion on top of it. */
class EconomyStageTest {

  private val generated: GeneratedWorld by lazy {
    StandardWorld.build(StandardWorld.demoConfig(seed = 77L).copy(widthCells = 160, heightCells = 160))
  }

  private val economies: List<PointMarker> by lazy {
    generated.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT_ECONOMY }
      .filterIsInstance<PointMarker>()
  }

  @Test
  fun `every standing settlement has an economy that feeds somebody`() {
    assertTrue(economies.isNotEmpty(), "no settlement got an economy")

    for (economy in economies) {
      val capacity = economy.attribute(EconomyChannels.FOOD_CAPACITY)
      assertTrue(
        capacity > 0.0 && capacity.isFinite(),
        "settlement ${economy.attribute(EconomyChannels.INDEX).toInt()} has food capacity $capacity"
      )
    }
  }

  @Test
  fun `employment adds up to the population`() {
    val chronicle = generated.world.chronicle
    val channels = listOf(
      EconomyChannels.FARMERS, EconomyChannels.CRAFTERS, EconomyChannels.TRADERS,
      EconomyChannels.SERVANTS, EconomyChannels.ADMINISTRATORS, EconomyChannels.CLERGY,
      EconomyChannels.SOLDIERS
    )

    for (economy in economies) {
      val index = economy.attribute(EconomyChannels.INDEX).toInt()
      val population = chronicle.settlements[index].population
      val jobs = channels.sumOf { economy.attribute(it).toInt() }

      assertTrue(
        Math.abs(jobs - population) <= 8,
        "settlement $index has $population people and $jobs jobs"
      )
    }
  }

  /**
   * A settlement whose land grows no grain has no baker, and the reason is printable.
   *
   * This is the headline claim of step 9 - that a roster follows from a place rather than from a roll - so it
   * is worth asserting directly rather than trusting the roster to look plausible. The synthetic setting makes
   * the claim exactly: same population, same wealth, only the cereal changes.
   */
  @Test
  fun `preconditions decide the roster, and say why`() {
    val fertile = setting(cerealShare = 0.6)
    val barren = setting(cerealShare = 0.0)

    val withBread = BusinessCatalogue.evaluate(fertile).first { it.type.id == "baker" }
    val without = BusinessCatalogue.evaluate(barren).first { it.type.id == "baker" }

    assertTrue(withBread.exists, "a town on good grain land has no baker: ${withBread.reason}")
    assertTrue(!without.exists, "a town that grows no grain has a baker anyway")
    assertTrue(
      without.reason.contains("cereal"),
      "the reason a barren town has no baker should name the cereal: '${without.reason}'"
    )
  }

  @Test
  fun `a mining town gets smiths and a fishing village gets fishmongers`() {
    val mining = BusinessCatalogue.evaluate(
      setting(cerealShare = 0.05, resources = setOf(ResourceType.IRON, ResourceType.TIMBER, ResourceType.COAL))
    )
    val port = BusinessCatalogue.evaluate(setting(cerealShare = 0.1, coastal = true))

    assertTrue(mining.first { it.type.id == "blacksmith" }.exists, "a town with iron and fuel has no smith")
    assertTrue(!mining.first { it.type.id == "baker" }.exists, "the mining town grows grain it does not have")
    assertTrue(port.first { it.type.id == "fishmonger" }.exists, "a coastal town has no fishmonger")
    assertTrue(
      !port.first { it.type.id == "blacksmith" }.exists,
      "a town with no iron has a smith"
    )
  }

  @Test
  fun `an inn count rises with road traffic`() {
    val quiet = BusinessCatalogue.evaluate(setting(traffic = 0.0)).first { it.type.id == "inn" }
    val crossroads = BusinessCatalogue.evaluate(setting(traffic = 8.0)).first { it.type.id == "inn" }

    assertTrue(
      crossroads.count > quiet.count,
      "a crossroads (${crossroads.count} inns) has no more inns than a backwater (${quiet.count})"
    )
  }

  @Test
  fun `a trade that cannot exist still reports a decision`() {
    // Every trade in the catalogue comes back, whether or not it produced anything. That is what the `why`
    // view prints, and a roster that only listed what exists could not answer "why is there no baker".
    val decisions = BusinessCatalogue.evaluate(setting())
    assertEquals(BusinessCatalogue.ALL.size, decisions.size)
    assertTrue(decisions.any { !it.exists }, "everything existed, so nothing was gated")
    for (decision in decisions) {
      assertTrue(decision.reason.isNotBlank(), "${decision.type.id} gave no reason")
    }
  }

  // --- Households -----------------------------------------------------------------------------------

  @Test
  fun `households expand from the stored summary alone`() {
    val index = economies.first().attribute(EconomyChannels.INDEX).toInt()
    val summary = EconomyProbe.summaryFor(generated.world, index)
    assertNotNull(summary, "settlement $index has an economy marker but no readable summary")

    val households = Households.expand(summary)
    assertEquals(summary.householdCount, households.size)
    assertTrue(households.all { it.size >= 1 }, "an empty household")

    // Expanding one household must not depend on having expanded the others - that is what makes it usable for
    // the single building a player walked into.
    val alone = Households.one(summary, households.size / 2)
    val together = households[households.size / 2]
    assertEquals(together.business, alone.business)
    assertEquals(together.size, alone.size)
    assertEquals(together.wealth, alone.wealth)
  }

  /**
   * The demographic pyramid is a pyramid.
   *
   * A town whose heads are all the same age with no children in it is a plausible-looking table and a broken
   * model - and the first thing to break it downstream would be an NPC schedule sending a child to work.
   */
  @Test
  fun `households have children and elders in plausible proportions`() {
    val summary = EconomyProbe.summaryFor(
      generated.world,
      // The biggest settlement, so the sample is large enough for shares to mean anything.
      economies.maxByOrNull { it.attribute(EconomyChannels.HOUSEHOLD_COUNT) }!!
        .attribute(EconomyChannels.INDEX).toInt()
    )!!

    val people = Households.expand(summary).flatMap { it.members }
    assertTrue(people.size > 50, "only ${people.size} people to look at")

    val children = people.count { it.age < 15 }.toDouble() / people.size
    assertTrue(children in 0.15..0.50, "children are ${"%.2f".format(children)} of the population")

    val old = people.count { it.age >= 60 }.toDouble() / people.size
    assertTrue(old in 0.0..0.20, "the over-sixties are ${"%.2f".format(old)} of the population")

    assertTrue(people.all { it.age >= 0 }, "somebody has a negative age")
  }

  @Test
  fun `the social graph is sparse and symmetric`() {
    val summary = EconomyProbe.summaryFor(
      generated.world,
      economies.maxByOrNull { it.attribute(EconomyChannels.HOUSEHOLD_COUNT) }!!
        .attribute(EconomyChannels.INDEX).toInt()
    )!!
    if (summary.householdCount < 10) return

    val graph = Households.socialGraph(summary)
    assertEquals(summary.householdCount, graph.size)

    for (a in graph.indices) {
      for (b in graph[a]) {
        assertTrue(b != a, "household $a is its own neighbour")
        assertTrue(a in graph[b].toList(), "household $a knows $b but not the other way round")
      }
    }

    val meanDegree = graph.sumOf { it.size }.toDouble() / graph.size
    assertTrue(meanDegree in 2.0..12.0, "mean degree is $meanDegree, which is not sparse")
  }

  @Test
  fun `roadside inns keep off the roads that have nobody on them`() {
    val inns = generated.world.features.all()
      .filter { it.kind == FeatureKind.ROADSIDE_INN }
      .filterIsInstance<PointMarker>()

    val roads = generated.world.features.all()
      .filter { it.kind == FeatureKind.ROAD }
      .filterIsInstance<net.bestia.worldgen.vector.PolylineFeature>()

    for (inn in inns) {
      val nearest = roads.minOf { it.centerline.project(inn.position).distance }
      assertTrue(nearest < 50.0, "inn ${inn.id} is ${nearest.toInt()} m from any road")
    }
  }

  private fun setting(
    population: Int = 1_500,
    wealth: Double = 0.6,
    traffic: Double = 2.0,
    coastal: Boolean = false,
    cerealShare: Double = 0.4,
    pasture: Double = 0.5,
    resources: Set<ResourceType> = setOf(ResourceType.TIMBER, ResourceType.STONE, ResourceType.CLAY),
    temperature: Double = 12.0,
    technology: Double = 0.5,
    garrison: Boolean = false
  ) = BusinessCatalogue.Setting(
    population = population,
    wealth = wealth,
    traffic = traffic,
    coastal = coastal,
    water = true,
    cerealShare = cerealShare,
    pasture = pasture,
    resources = resources,
    temperature = temperature,
    technology = technology,
    garrison = garrison
  )
}
