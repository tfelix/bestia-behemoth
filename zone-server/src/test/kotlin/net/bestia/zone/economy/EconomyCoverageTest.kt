package net.bestia.zone.economy

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The boot checks that need the item and recipe catalogues.
 *
 * I21 is the one worth writing first, and it needs a test of its own for an awkward reason: no recipe
 * the game ships is made entirely of priced items, so the shipped check passes by having nothing to
 * look at. A check that has never been seen to fail is not a check, so the printer below is synthetic -
 * what it proves is that the day iron gets a producer, the boot stops.
 */
class EconomyCoverageTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val recipes = RecipeRegistry()
  private val items = mockk<ItemRepository>()

  init {
    every { items.findByIdentifier(any()) } returns null
    KNOWN.forEach { (identifier, id) ->
      every { items.findByIdentifier(identifier) } returns Item(id = id, identifier = identifier, weight = 1, type = Item.ItemType.ETC)
    }
  }

  @Test
  fun `I22 - every trade the generator builds has an economy entry`() {
    recipes.load(emptyList())

    coverage().check()
  }

  @Test
  fun `and the entries cover the catalogue exactly, with nothing left over`() {
    val named = catalogue.trades().mapNotNull { it.business } +
      catalogue.retailTrades() +
      catalogue.unboundTrades().map { it.business }

    assertTrue(
      named.toSet() == BusinessCatalogue.ALL.map { it.id }.toSet() && named.size == named.toSet().size,
      "economy.yml and BusinessCatalogue have drifted apart"
    )
  }

  @Test
  fun `an unbound trade waiting for an item that now exists has to be bound`() {
    // What makes the blocked list prune itself. Ale does not exist, so pretending it does is the same
    // thing that happens the day somebody adds it.
    every { items.findByIdentifier("ale") } returns Item(id = 900, identifier = "ale", weight = 1, type = Item.ItemType.ETC)
    recipes.load(emptyList())

    val error = assertFailsWith<IllegalArgumentException> { coverage().check() }

    assertTrue(error.message!!.contains("brewer"), "the message does not name the trade: ${error.message}")
  }

  @Test
  fun `I21 - a recipe that turns cheap NPC goods into a dear one is refused`() {
    // Grain into bread at three sacks a loaf: profitable at the bounds, because bread is only three
    // times the price of grain and the band between the floor and the ceiling is eight times wide.
    recipes.load(listOf(bake(grainPerLoaf = 3, chance = 1.0f)))

    val error = assertFailsWith<IllegalArgumentException> { coverage().check() }

    assertTrue(error.message!!.contains("BAKE"), "the message does not name the recipe: ${error.message}")
  }

  @Test
  fun `and one that is loss-making at the bounds passes`() {
    // The same recipe with enough grain in it. Twenty-five sacks a loaf is absurd bread and exactly the
    // point: at the extremes of the price band, crafting has to be a bad way to make money. Twenty-four
    // is break-even to the penny and is refused too, because "not profitable" is the wrong bar.
    recipes.load(listOf(bake(grainPerLoaf = 25, chance = 1.0f)))

    coverage().check()
  }

  @Test
  fun `a low success chance is what usually saves a recipe, and it is charged for`() {
    // Inputs are consumed on a failure too, so the same recipe that prints at full success does not at
    // one attempt in ten.
    recipes.load(listOf(bake(grainPerLoaf = 3, chance = 0.1f)))

    coverage().check()
  }

  private fun coverage() = EconomyCoverage(catalogue, recipes, items)

  private fun bake(grainPerLoaf: Int, chance: Float) = Recipe(
    id = 1,
    identifier = "BAKE",
    effect = RecipeEffect.PRODUCE,
    output = Recipe.ItemStack(itemId = KNOWN.getValue("bread"), amount = 1),
    inputs = listOf(Recipe.ItemStack(itemId = KNOWN.getValue("grain"), amount = grainPerLoaf)),
    requiredSkillId = 1,
    requiredSkillLevel = 1,
    station = null,
    craftSeconds = 4f,
    baseSuccessChance = chance,
  )

  private companion object {
    val KNOWN = mapOf("grain" to 28L, "flour" to 29L, "bread" to 30L)
  }
}
