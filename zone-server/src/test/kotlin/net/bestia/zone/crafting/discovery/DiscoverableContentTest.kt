package net.bestia.zone.crafting.discovery

import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.crafting.gear.GearRecipeGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles

/** Every recipe outside the starter kit has to be findable, or nobody can ever make it. */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class DiscoverableContentTest {

  @Autowired
  private lateinit var recipes: RecipeRegistry

  @Autowired
  private lateinit var blueprints: BlueprintPoints

  @Test
  fun `the blueprints outside the starter kit can be discovered`() {
    val discoverable = recipes.all().flatMap { blueprints.discoverable(it.requiredSkillId) }
      .map { it.recipe.identifier }.toSet()

    // The equipment catalogue is generated into `gear_recipes.yml`, and every piece of it is discovered.
    val gear = ClassPathResource(GearRecipeGenerator.OUTPUT_FILE).inputStream
      .use { GearRecipeGenerator.idsIn(String(it.readAllBytes())) }.keys
    val others = setOf(
      "WEAPON_BLUEPRINT", "ARMOR_BLUEPRINT", "WAR_BANNER", "BAMBOO_ROD", "ASH_ROD", "STEEL_ROD",
      "SMALL_HEALTH_POTION", "SMALL_MANA_POTION", "HEALTH_POTION", "MANA_POTION",
      "PURE_MANA_ESSENCE", "MANA_CONCENTRATE", "YELLOW_MANA_CRYSTAL", "RED_MANA_CRYSTAL",
      "WEAPON_REPAIR_KIT", "ARMOR_REPAIR_KIT",
      // The Alchemist's brews and transmutations: every input has craft-axes.
      "ALCHEMICAL_SALT", "MEDICINE", "ANTIDOTE", "HERBAL_EXTRACT", "VIGOR_DRAUGHT", "WARMING_DRAUGHT", "CATALYST",
      "SMELTING_FLUX", "BURN_SALVE", "BLASTING_CHARGE", "MANA_TINCTURE", "SWIFTNESS_POTION", "REGENERATION_DRAUGHT",
      "CLARITY_DRAUGHT", "CRYSTAL_VIAL", "ELIXIR_OF_MIGHT", "ELIXIR_OF_FORTITUDE", "ELIXIR_OF_GRACE",
      "ELIXIR_OF_PRECISION", "ELIXIR_OF_INSIGHT", "GREATER_HEALTH_POTION", "GREATER_MANA_POTION", "ELIXIR_OF_RESOLVE",
      "THAWING_TONIC", "ALKAHEST", "ENDURANCE_TONIC", "COOLING_DRAUGHT", "REJUVENATION_POTION", "STONE_SOFTENER",
      "BARRIER_POTION", "SUPERIOR_HEALTH_POTION", "SUPERIOR_MANA_POTION", "GREATER_ELIXIR_OF_PRECISION",
      "GREATER_ELIXIR_OF_MIGHT", "PANACEA", "GREATER_ELIXIR_OF_GRACE", "QUINTESSENCE", "SUPREME_HEALTH_POTION",
      "SUPREME_MANA_POTION", "GREATER_ELIXIR_OF_INSIGHT", "GREATER_ELIXIR_OF_FORTITUDE", "GREATER_ELIXIR_OF_RESOLVE",
      "SLIM_SUPREME_HEALTH_POTION", "SLIM_SUPREME_MANA_POTION", "ELIXIR_OF_LIFE", "PHILOSOPHERS_DRAUGHT",
      "QUICKSILVER_TRANSMUTED", "ARCANE_FLUX", "PHILOSOPHERS_STONE",
    )

    assertTrue(gear.size >= 340, "expected the whole equipment catalogue, found ${gear.size}")
    assertEquals(others + gear, discoverable)
  }
}
