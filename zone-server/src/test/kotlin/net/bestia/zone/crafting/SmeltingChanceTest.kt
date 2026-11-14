package net.bestia.zone.crafting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max

/**
 * The shipped refining recipes against the docs' smelting table (`natural-resources.md`, "Ore Levels"):
 * `0.30 - max(0, oreLevel - 10) * 0.01`, which is below zero from ore level 40 up. A recipe holds that number as
 * it is, because a high ore really does start below zero and only skill, stats and bonuses lift it.
 */
class SmeltingChanceTest {

  private val catalog = CraftingCatalog()

  @Test
  fun `every ore refines at the docs' base chance for its level`() {
    val smelts = catalog.recipes.all().filter { recipe ->
      recipe.inputs.any { catalog.item(it.itemId).identifier.endsWith("_ore") }
    }

    assertTrue(smelts.size >= 9, "expected the metal chain, found ${smelts.map { it.identifier }}")
    smelts.forEach { recipe ->
      val ore = recipe.inputs.map { catalog.item(it.itemId) }.first { it.identifier.endsWith("_ore") }
      val expected = 0.30f - max(0, ore.baseLevel - 10) * 0.01f

      assertEquals(expected, recipe.baseSuccessChance, 1e-4f, "${recipe.identifier} smelts ${ore.identifier} (Lv. ${ore.baseLevel})")
    }
  }

  @Test
  fun `the high ores start below zero`() {
    val negative = catalog.recipes.all().filter { it.baseSuccessChance < 0f }.map { it.identifier }.toSet()

    assertTrue(negative.containsAll(setOf("GOLD_BAR", "QUICKSILVER", "MITHRIL_INGOT", "ADAMANTIUM_INGOT")), "$negative")
  }
}
