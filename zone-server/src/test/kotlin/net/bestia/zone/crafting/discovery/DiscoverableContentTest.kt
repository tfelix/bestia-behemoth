package net.bestia.zone.crafting.discovery

import net.bestia.zone.crafting.RecipeRegistry
import org.junit.jupiter.api.Assertions.assertEquals
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

    assertEquals(setOf("WEAPON_BLUEPRINT", "ARMOR_BLUEPRINT", "WAR_BANNER"), discoverable)
  }
}
