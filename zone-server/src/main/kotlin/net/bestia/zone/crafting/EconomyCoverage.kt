package net.bestia.zone.crafting

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.zone.util.CatalogValidator
import net.bestia.zone.item.persistence.ItemRepository
import org.springframework.stereotype.Component
import net.bestia.zone.economy.Commodity
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.EconomyCatalogue
import net.bestia.zone.economy.Shop

/**
 * The economy checks that need catalogues the boot fills in later - the items and the recipes are written
 * by `CommandLineRunner`s, so this runs from [net.bestia.zone.boot.ContentValidationBootRunner].
 *
 * Three failures, all of them silent without a boot check:
 *
 * - **I22.** A trade with no entry is a shop standing in every town in the world with nobody in it.
 * - **A stale block.** An `unbound` entry naming an item that now exists is a trade somebody could have
 *   bound and did not. Refusing it makes the list prune itself.
 * - **I21.** A crafting loop that pays inside one town is a gold printer, and it looks exactly like a
 *   well-priced recipe until somebody runs it ten thousand times.
 */
@Component
class EconomyCoverage(
  private val catalogue: EconomyCatalogue,
  private val recipes: RecipeRegistry,
  private val items: ItemRepository,
  private val commodityItems: CommodityItems,
) : CatalogValidator {

  override fun validate() {
    val priced = commodityItems.priced()

    checkEveryTradeIsAccountedFor()
    checkCommodityItemsExist()
    checkCoinExists()
    checkBlockedItemsAreStillMissing()
    checkNoCraftingLoopPrints(priced)

    LOG.info {
      "Economy binds ${catalogue.trades().size} trades, ${priced.size} priced items; " +
        "${catalogue.unboundTrades().size} trades still produce nothing"
    }
  }

  /** I22: every business in the generator's catalogue produces, retails, or says why it does neither. */
  private fun checkEveryTradeIsAccountedFor() {
    val all = BusinessCatalogue.ALL.map { it.id }.toSet()
    val produced = catalogue.trades().mapNotNull { it.business }
    val retail = catalogue.retailTrades()
    val unbound = catalogue.unboundTrades().map { it.business }

    val named = produced + retail + unbound
    val unknown = named.filterNot { it in all }
    require(unknown.isEmpty()) { "economy.yml names trades BusinessCatalogue does not have: ${unknown.sorted()}" }

    val twice = named.groupBy { it }.filterValues { it.size > 1 }.keys
    require(twice.isEmpty()) { "These trades are listed more than once: ${twice.sorted()}" }

    val orphaned = all - named.toSet()
    require(orphaned.isEmpty()) {
      "These trades have no economy entry, so every town in the world would build them and leave them " +
        "empty: ${orphaned.sorted()}"
    }
  }

  private fun checkCommodityItemsExist() {
    val missing = catalogue.commodities().map { it.item }.filter { items.findByIdentifier(it) == null }
    require(missing.isEmpty()) {
      "These commodities name items the catalogue does not have, so nothing could ever be handed over: " +
        missing.sorted()
    }
  }

  /** Nothing can be bought or sold without it, and it is one line in `items.yml` away from being absent. */
  private fun checkCoinExists() {
    requireNotNull(commodityItems.coinItemId()) {
      "There is no '${CommodityItems.COIN}' item, so no shop could take payment for anything"
    }
  }

  private fun checkBlockedItemsAreStillMissing() {
    val arrived = catalogue.unboundTrades()
      .filter { it.needs != null && items.findByIdentifier(it.needs) != null }
      .map { "${it.business} (waiting for ${it.needs})" }

    require(arrived.isEmpty()) {
      "These trades are waiting for items that now exist, so they can be bound: ${arrived.sorted()}"
    }
  }

  /**
   * I21: no buy-craft-sell loop pays inside one town.
   *
   * Not across the price band: buying cheap in one town and selling dear in another is hauling, which is
   * intended play, and measured that way every recipe worth crafting would be a printer.
   */
  private fun checkNoCraftingLoopPrints(priced: Map<Long, Commodity>) {
    val printers = recipes.all()
      .filter { it.effect == RecipeEffect.PRODUCE }
      .mapNotNull { recipe -> profitInOneTown(recipe, priced)?.let { recipe to it } }
      .filter { (_, profit) -> profit >= 0.0 }
      .map { (recipe, profit) -> "${recipe.identifier} (+${"%.1f".format(profit)} per attempt)" }

    require(printers.isEmpty()) {
      "These recipes turn NPC goods into money inside one town: ${printers.sorted()}"
    }
  }

  /** Null when any of the recipe's items has no price, which makes the loop unrunnable against NPCs. */
  private fun profitInOneTown(recipe: Recipe, priced: Map<Long, Commodity>): Double? {
    val output = recipe.output ?: return null
    val sold = priced[output.itemId] ?: return null
    val bought = recipe.inputs.map { priced[it.itemId] ?: return null }

    // Reference prices, because a town's own multiplier scales both sides alike. Inputs are consumed on a
    // failure too, and a base below zero is a craft that only skill lifts into reach.
    val revenue = sold.refPrice * (1.0 - Shop.SPREAD) * output.amount * maxOf(0f, recipe.baseSuccessChance)
    val cost = recipe.inputs.zip(bought).sumOf { (stack, commodity) ->
      commodity.refPrice * (1.0 + Shop.SPREAD) * stack.amount
    }

    return revenue - cost
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
