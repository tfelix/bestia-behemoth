package net.bestia.zone.crafting.discovery

import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.StarterKit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BlueprintPointsTest {

  private val axes = MaterialAxes().apply {
    load(
      mapOf(
        HERB to CraftVector(mapOf(CraftAxis.STATE to -1.0, CraftAxis.VITALITY to 0.9)),
        WATER to CraftVector(mapOf(CraftAxis.STATE to -1.0)),
      )
    )
  }

  @Test
  fun `a blueprint sits where its own inputs mix`() {
    val potion = recipe(1, "POTION", HERB to 1, WATER to 2)
    val points = BlueprintPoints().apply { load(listOf(potion), axes, StarterKit()) }

    assertEquals(0.3, points.pointOf(potion.id)!![CraftAxis.VITALITY], 1e-9)
    assertEquals(listOf(potion), points.discoverable(SKILL).map { it.recipe })
  }

  @Test
  fun `a starter blueprint is placed but not discoverable`() {
    val potion = recipe(1, "POTION", HERB to 1)
    val starters = StarterKit().apply { load(setOf(potion.id)) }
    val points = BlueprintPoints().apply { load(listOf(potion), axes, starters) }

    assertEquals(emptyList<BlueprintPoints.Blueprint>(), points.discoverable(SKILL))
  }

  @Test
  fun `an input without axes leaves its recipe without a point`() {
    val odd = recipe(1, "ODD", HERB to 1, UNPLACED to 1)
    val points = BlueprintPoints().apply { load(listOf(odd), axes, StarterKit()) }

    assertNull(points.pointOf(odd.id))
  }

  @Test
  fun `two blueprints of one skill on one point fail the boot`() {
    val small = recipe(1, "SMALL", HERB to 1, WATER to 1)
    val large = recipe(2, "LARGE", HERB to 3, WATER to 3)

    assertThrows<IllegalArgumentException> { BlueprintPoints().load(listOf(small, large), axes, StarterKit()) }
  }

  private fun recipe(id: Long, identifier: String, vararg inputs: Pair<Long, Int>) = Recipe(
    id = id,
    identifier = identifier,
    effect = RecipeEffect.PRODUCE,
    output = Recipe.ItemStack(itemId = 100L + id, amount = 1),
    inputs = inputs.map { (itemId, amount) -> Recipe.ItemStack(itemId, amount) },
    requiredSkillId = SKILL,
    requiredSkillLevel = 1,
    station = null,
    craftSeconds = 1f,
    baseSuccessChance = 1f,
  )

  private companion object {
    const val SKILL = 7L
    const val HERB = 1L
    const val WATER = 2L
    const val UNPLACED = 3L
  }
}
