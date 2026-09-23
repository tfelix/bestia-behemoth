package net.bestia.zone.economy

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.resource.GradeMix
import net.bestia.worldgen.resource.OreGrade
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.world.mining.OreYield
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.math.abs

/**
 * Checks that digging a voxel and minting what comes out is worth what the economy says it is.
 *
 * The coin supply, and with it the size of the world, is quoted per ore voxel. Nothing enforces that: the
 * grade yields are in Kotlin, the ore-per-bar ratio and the coin-per-bar are in a yml file, and the
 * figure they are all supposed to reproduce is in another. Each is reasonable on its own and any one of
 * them can be changed without the others - and the failure is silent, because a world simply ends up with
 * more or less money in it than it was sized for.
 */
@Component
class MintChainValidator(
  private val recipes: RecipeRegistry,
  private val items: ItemRepository,
  private val config: EconomyConfig,
) {

  @EventListener(ApplicationReadyEvent::class)
  fun check() {
    val mint = recipes.all().firstOrNull { it.identifier == MINT_RECIPE }
    if (mint == null) {
      LOG.info { "No $MINT_RECIPE recipe, so no coin can be minted; the supply is whatever NPCs were given" }
      return
    }

    val smelt = recipes.all().firstOrNull { it.identifier == SMELT_RECIPE }
    requireNotNull(smelt) { "$MINT_RECIPE mints from a bar that $SMELT_RECIPE is supposed to make, and it is missing" }

    val oreId = items.findByIdentifier(ORE)?.id
    requireNotNull(oreId) { "The mint chain starts at '$ORE', which items.yml does not have" }

    val orePerBar = smelt.inputs.firstOrNull { it.itemId == oreId }?.amount
    require(orePerBar != null && orePerBar > 0) { "$SMELT_RECIPE does not consume any '$ORE'" }

    val coinsPerVoxel = orePerVoxel() / orePerBar * mint.output!!.amount
    val drift = abs(coinsPerVoxel - config.coinsPerOreVoxel)

    require(drift <= config.coinsPerOreVoxel * TOLERANCE) {
      "An ore voxel mints ${"%.1f".format(coinsPerVoxel)} coins, but the money supply is sized at " +
        "${config.coinsPerOreVoxel}. Either the grade yields, the ore per bar, the coin per bar or " +
        "economy.coins-per-ore-voxel has moved without the others."
    }

    LOG.info { "Mint chain: an ore voxel is worth ${"%.1f".format(coinsPerVoxel)} coin(s)" }
  }

  /** Lumps of ore a voxel yields on average, over how often the three grades come up. */
  private fun orePerVoxel(): Double {
    val mix = GradeMix()
    val weights = mapOf(
      OreGrade.SMALL to mix.smallWeight,
      OreGrade.MEDIUM to mix.mediumWeight,
      OreGrade.RICH to mix.richWeight,
    )

    val weighted = weights.entries.sumOf { (grade, weight) -> weight * OreYield.amountFor(grade) }

    return weighted / weights.values.sum()
  }

  private companion object {
    const val MINT_RECIPE = "GOLD_COIN"
    const val SMELT_RECIPE = "GOLD_BAR"
    const val ORE = "gold_ore"

    /** Room for a rounding, not for a re-balance: a percent is far tighter than any of these move by. */
    const val TOLERANCE = 0.01

    val LOG = KotlinLogging.logger { }
  }
}
