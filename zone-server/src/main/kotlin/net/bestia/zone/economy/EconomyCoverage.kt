package net.bestia.zone.economy

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.item.ItemRepository
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * The economy checks that need catalogues the boot fills in later - the items and the recipes are written
 * by `CommandLineRunner`s, so none of this can run at construction. `OccupationCoverage`'s shape.
 *
 * Three failures, all of them silent without a boot check:
 *
 * - **I22.** A trade with no entry is a shop standing in every town in the world with nobody in it.
 * - **A stale block.** An `unbound` entry naming an item that now exists is a trade somebody could have
 *   bound and did not. Refusing it makes the list prune itself.
 * - **I21.** A crafting loop that is profitable at the price bounds is a gold printer, and it looks
 *   exactly like a well-priced recipe until somebody runs it ten thousand times.
 */
@Component
class EconomyCoverage(
  private val catalogue: EconomyCatalogue,
  private val recipes: RecipeRegistry,
  private val items: ItemRepository,
  private val commodityItems: CommodityItems,
) {

  @EventListener(ApplicationReadyEvent::class)
  fun check() {
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
   * I21: no buy-craft-sell loop pays, even at the extremes of the price band.
   *
   * The paranoid case on purpose: inputs bought where they are at the floor, the output sold where it is
   * at the ceiling, and the recipe's success chance charged against it because inputs are consumed on a
   * failure too. That is a player carrying goods between two towns, which is intended play - what must
   * not be intended is the *crafting* leg turning a price gap into money from nothing.
   *
   * Only recipes made entirely of priced items are checkable, and today none is: nothing an NPC sells is
   * an input to anything a player forges. The check is here rather than later because the day iron gets a
   * producer it has to fail loudly, not be remembered.
   */
  private fun checkNoCraftingLoopPrints(priced: Map<Long, Commodity>) {
    val printers = recipes.all()
      .filter { it.effect == RecipeEffect.PRODUCE }
      .mapNotNull { recipe -> profitAtExtremes(recipe, priced)?.let { recipe to it } }
      .filter { (_, profit) -> profit >= 0.0 }
      .map { (recipe, profit) -> "${recipe.identifier} (+${"%.1f".format(profit)} per attempt)" }

    require(printers.isEmpty()) {
      "These recipes turn NPC goods into money at the price bounds: ${printers.sorted()}"
    }
  }

  /** Null when any of the recipe's items has no price, which makes the loop unrunnable against NPCs. */
  private fun profitAtExtremes(recipe: Recipe, priced: Map<Long, Commodity>): Double? {
    val output = recipe.output ?: return null
    val sold = priced[output.itemId] ?: return null
    val bought = recipe.inputs.map { priced[it.itemId] ?: return null }

    val revenue = PriceCurve.CEILING * sold.refPrice * output.amount * recipe.baseSuccessChance
    val cost = recipe.inputs.zip(bought).sumOf { (stack, commodity) ->
      PriceCurve.FLOOR * commodity.refPrice * stack.amount
    }

    return revenue - cost
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
