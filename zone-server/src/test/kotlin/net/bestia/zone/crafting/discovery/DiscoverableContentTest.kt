package net.bestia.zone.crafting.discovery

import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.crafting.gear.GearRecipeGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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

    // The equipment catalogue is generated into `gear_recipes.yml` from id 200 up, and every piece of it is discovered.
    val gear = recipes.all().filter { it.id >= GearRecipeGenerator.FIRST_ID }.map { it.identifier }.toSet()
    val others = setOf(
      "WEAPON_BLUEPRINT", "ARMOR_BLUEPRINT", "WAR_BANNER", "BAMBOO_ROD", "ASH_ROD", "STEEL_ROD",
      "SMALL_HEALTH_POTION", "SMALL_MANA_POTION", "HEALTH_POTION", "MANA_POTION",
      "PURE_MANA_ESSENCE", "MANA_CONCENTRATE", "YELLOW_MANA_CRYSTAL", "RED_MANA_CRYSTAL",
    )

    assertTrue(gear.size >= 340, "expected the whole equipment catalogue, found ${gear.size}")
    assertEquals(others + gear, discoverable)
  }
}
