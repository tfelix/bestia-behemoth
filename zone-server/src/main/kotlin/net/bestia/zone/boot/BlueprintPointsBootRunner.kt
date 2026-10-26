package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.crafting.StarterKit
import net.bestia.zone.crafting.discovery.BlueprintPoints
import net.bestia.zone.crafting.discovery.MaterialAxes
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Places every blueprint once items (100), recipes (104) and the starter kit (105) are loaded. */
@Component
@Order(106)
class BlueprintPointsBootRunner(
  private val recipeRegistry: RecipeRegistry,
  private val materialAxes: MaterialAxes,
  private val starterKit: StarterKit,
  private val blueprintPoints: BlueprintPoints,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    blueprintPoints.load(recipeRegistry.all(), materialAxes, starterKit)

    LOG.info { "Blueprint points placed" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
