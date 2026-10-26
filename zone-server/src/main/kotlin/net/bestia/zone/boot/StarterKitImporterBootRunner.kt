package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.crafting.StarterKit
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Loads `starter_kit.yml` into [StarterKit], resolving each blueprint's recipe identifier. Ordered after the recipe
 * importer (104), which it resolves against.
 */
@Component
@Order(105)
class StarterKitImporterBootRunner(
  private val recipeRegistry: RecipeRegistry,
  private val starterKit: StarterKit,
) : CommandLineRunner {

  data class StarterKitYmlFile(
    val blueprints: List<String> = emptyList()
  )

  override fun run(vararg args: String?) {
    val objectMapper = ObjectMapper(YAMLFactory()).apply { registerKotlinModule() }

    val dto = ClassPathResource(STARTER_KIT_RESOURCE).inputStream.use {
      objectMapper.readValue(it, StarterKitYmlFile::class.java)
    }

    apply(dto.blueprints)
  }

  fun apply(blueprintIdentifiers: List<String>) {
    val blueprintIds = blueprintIdentifiers.map { recipeIdOf(it) }.toSet()
    starterKit.load(blueprintIds)

    LOG.info { "Starter kit loaded: ${blueprintIds.size} blueprints" }
  }

  /** Only a producing recipe is a blueprint; repairing or upgrading is something a skill does. */
  private fun recipeIdOf(identifier: String): Long {
    val recipe = recipeRegistry.ofIdentifier(identifier)
      ?: error("starter_kit.yml names unknown recipe '$identifier'")
    require(recipe.effect == RecipeEffect.PRODUCE) {
      "starter_kit.yml names '$identifier', which is ${recipe.effect} rather than a blueprint"
    }

    return recipe.id
  }

  companion object {
    private const val STARTER_KIT_RESOURCE = "starter_kit.yml"

    private val LOG = KotlinLogging.logger { }
  }
}
