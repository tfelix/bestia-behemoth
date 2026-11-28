package net.bestia.zone.crafting

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.EconomyCatalogue
import net.bestia.zone.item.material.ItemMaterial

/**
 * The boot checks that need the item and recipe catalogues.
 *
 * I21 needs a synthetic printer: a shipped recipe that failed it would stop the boot, so the only way to
 * see the check fail is a recipe made up for the test.
 */
class EconomyCoverageTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val recipes = RecipeRegistry()
  private val items = mockk<ItemRepository>()

  init {
    every { items.findByIdentifier(any()) } returns null
    // Coin has to exist or every check below trips over the one that says so.
    every { items.findByIdentifier(CommodityItems.COIN) } returns
      Item(id = 26, identifier = CommodityItems.COIN, weight = 0, type = Item.ItemType.ETC, material = ItemMaterial.METAL)
    KNOWN.forEach { (identifier, id) ->
      every { items.findByIdentifier(identifier) } returns Item(id = id, identifier = identifier, weight = 1, type = Item.ItemType.ETC, material = ItemMaterial.METAL)
    }
  }

  @Test
  fun `I22 - every trade the generator builds has an economy entry`() {
    recipes.load(emptyList())

    coverage().validate()
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
    // What makes the blocked list prune itself, and it has nothing live to act on: every blocked trade's
    // goods are in items.yml now, so no entry names an item any more. This is the shape of the next one
    // somebody blocks.
    every { items.findByIdentifier("ale") } returns Item(id = 900, identifier = "ale", weight = 1, type = Item.ItemType.ETC, material = ItemMaterial.FOOD)
    recipes.load(emptyList())

    val error = assertFailsWith<IllegalArgumentException> { coverage(stillWaitingFor("brewer", "ale")).validate() }

    assertTrue(error.message!!.contains("brewer"), "the message does not name the trade: ${error.message}")
  }

  @Test
  fun `I21 - a recipe that turns cheap NPC goods into a dear one is refused`() {
    // Grain into bread at two sacks a loaf: six coins of grain become nine of bread in the same town,
    // which still pays after the spread on both legs.
    recipes.load(listOf(bake(grainPerLoaf = 2, chance = 1.0f)))

    val error = assertFailsWith<IllegalArgumentException> { coverage().validate() }

    assertTrue(error.message!!.contains("BAKE"), "the message does not name the recipe: ${error.message}")
  }

  @Test
  fun `and one that is loss-making in one town passes`() {
    // Three sacks cost more than the loaf sells for once the spread is paid on both legs.
    recipes.load(listOf(bake(grainPerLoaf = 3, chance = 1.0f)))

    coverage().validate()
  }

  @Test
  fun `a low success chance is what usually saves a recipe, and it is charged for`() {
    // Inputs are consumed on a failure too, so the same recipe that prints at full success does not at
    // one attempt in two.
    recipes.load(listOf(bake(grainPerLoaf = 2, chance = 0.5f)))

    coverage().validate()
  }

  private fun coverage(catalogue: EconomyCatalogue = this.catalogue) =
    EconomyCoverage(catalogue, recipes, items, CommodityItems(catalogue, items))

  /**
   * The shipped catalogue with one trade put back to waiting for an item.
   *
   * Only the entry changes: a hand-built catalogue would have to name all thirty trades to get past I22
   * first, and would then be testing a roster nobody ships.
   */
  private fun stillWaitingFor(business: String, item: String): EconomyCatalogue {
    val spy = mockk<EconomyCatalogue>()
    every { spy.commodities() } returns catalogue.commodities()
    every { spy.trades() } returns catalogue.trades()
    every { spy.retailTrades() } returns catalogue.retailTrades()
    every { spy.unboundTrades() } returns catalogue.unboundTrades().map {
      if (it.business == business) EconomyCatalogue.Unbound(business, item, it.reason) else it
    }

    return spy
  }

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
    val KNOWN = mapOf(
      "grain" to 28L, "flour" to 29L, "bread" to 30L, "weapon_repair_kit" to 641L, "armor_repair_kit" to 642L,
      "log" to 59L, "plank" to 60L, "charcoal" to 623L,
      "iron_ingot" to 9L, "nails" to 75L,
    )
  }
}
