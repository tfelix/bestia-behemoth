package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.crafting.StarterKit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.ClassPathResource

class StarterKitImportTest {

  private val stew = recipe(id = 1, identifier = "STEW", effect = RecipeEffect.PRODUCE)
  private val banner = recipe(id = 2, identifier = "BANNER", effect = RecipeEffect.PRODUCE)
  private val repair = recipe(id = 3, identifier = "REPAIR", effect = RecipeEffect.REPAIR)

  private val starterKit = StarterKit()
  private val runner = StarterKitImporterBootRunner(
    RecipeRegistry().apply { load(listOf(stew, banner, repair)) },
    starterKit
  )

  @Test
  fun `a listed blueprint is known from the start, an unlisted one is not`() {
    runner.apply(listOf("STEW"))

    assertTrue(starterKit.isStarterBlueprint(stew.id))
    assertFalse(starterKit.isStarterBlueprint(banner.id))
  }

  @Test
  fun `an unknown recipe fails the boot`() {
    assertThrows<IllegalStateException> { runner.apply(listOf("NOPE")) }
  }

  @Test
  fun `a skill action is not a blueprint`() {
    assertThrows<IllegalArgumentException> { runner.apply(listOf("REPAIR")) }
  }

  /** Reads the real files the way the importers do, so a renamed recipe fails here and not at boot. */
  @Test
  fun `every starter blueprint is a producing recipe in recipes yml`() {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    val starters = ClassPathResource("starter_kit.yml").inputStream.use {
      mapper.readValue(it, StarterKitImporterBootRunner.StarterKitYmlFile::class.java).blueprints
    }
    val recipes = ClassPathResource("recipes.yml").inputStream.use {
      mapper.readValue(it, RecipeImporterBootRunner.RecipesYmlFile::class.java).recipes
    }.associateBy { it.identifier }

    starters.forEach { identifier ->
      assertEquals("PRODUCE", recipes[identifier]?.effect, "starter blueprint $identifier")
    }
  }

  private fun recipe(id: Long, identifier: String, effect: RecipeEffect) = Recipe(
    id = id,
    identifier = identifier,
    effect = effect,
    output = if (effect == RecipeEffect.PRODUCE) Recipe.ItemStack(itemId = 9L, amount = 1) else null,
    inputs = emptyList(),
    requiredSkillId = 1L,
    requiredSkillLevel = 1,
    station = null,
    craftSeconds = 1f,
    baseSuccessChance = 1f,
  )
}
