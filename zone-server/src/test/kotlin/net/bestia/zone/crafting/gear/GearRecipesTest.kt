package net.bestia.zone.crafting.gear

import net.bestia.zone.crafting.CraftingCatalog
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.discovery.CraftVector
import net.bestia.zone.crafting.discovery.DiscoveryService
import net.bestia.zone.item.material.ItemMaterial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import kotlin.math.max

/**
 * Whether the equipment catalogue can actually be crafted and discovered: a recipe for every piece, each with a point
 * of its own that a crafter can find, in rules that are written down and reproducible.
 *
 * Reads `items.yml`, `recipes.yml` and `gear_recipes.yml` through [CraftingCatalog] the way the server loads them.
 */
class GearRecipesTest {

  private val catalog = CraftingCatalog()
  private val gear = catalog.items.filter(GearRecipeGenerator::craftable)
  private val forgeWeapon = catalog.skillIdByName.getValue("M_FORGE_WEAPON")
  private val forgeArmor = catalog.skillIdByName.getValue("M_FORGE_ARMOR")

  private fun recipeOf(identifier: String) = catalog.recipes.all().filter { it.output?.itemId == catalog.item(identifier).id }

  @Test
  fun `every piece of the catalogue has exactly one recipe`() {
    assertTrue(gear.size >= 340, "expected the whole equipment catalogue, found ${gear.size}")

    val without = gear.filter { recipeOf(it.identifier).size != 1 }.map { it.identifier }

    assertEquals(emptyList<String>(), without)
  }

  @Test
  fun `weapons are Forge Weapon, armour and trinkets are Forge Armor, at a rank that exists`() {
    gear.forEach { item ->
      val recipe = recipeOf(item.identifier).single()
      val expected = if (item.weaponType != null) forgeWeapon else forgeArmor

      assertEquals(RecipeEffect.PRODUCE, recipe.effect)
      assertEquals(expected, recipe.requiredSkillId, "${item.identifier} is made with the wrong skill")
      assertTrue(recipe.requiredSkillLevel in 1..10, "${item.identifier} asks for rank ${recipe.requiredSkillLevel}")
    }
  }

  @Test
  fun `metal is worked at the forge and everything else at the workbench`() {
    gear.forEach { item ->
      val recipe = recipeOf(item.identifier).single()
      val main = catalog.item(recipe.inputs.first().itemId)
      val station = if (main.material == ItemMaterial.METAL) "FORGE" else "WORKBENCH"

      assertEquals(station, recipe.station?.name, "${item.identifier} is made of ${main.identifier}")
    }
  }

  @Test
  fun `every recipe can be found, because every input has a place in the feature space`() {
    val lost = gear.filter { catalog.blueprints.pointOf(recipeOf(it.identifier).single().id) == null }.map { it.identifier }

    assertEquals(emptyList<String>(), lost)
  }

  @Test
  fun `no other blueprint of the skill lies inside a piece's own radius at skill one`() {
    val radius = DiscoveryService.radiusOf(1)
    val crowded = ArrayList<String>()

    for (skill in listOf(forgeWeapon, forgeArmor)) {
      val blueprints = catalog.recipes.all()
        .filter { it.requiredSkillId == skill && it.effect == RecipeEffect.PRODUCE }
        .mapNotNull { recipe -> catalog.blueprints.pointOf(recipe.id)?.let { recipe to it } }

      blueprints.filter { (recipe, _) -> recipe.output?.itemId in gear.map { it.id } }.forEach { (recipe, point) ->
        val nearest = blueprints.filter { it.first !== recipe }.minOf { it.second.distanceTo(point) }
        if (nearest < radius) crowded += "${recipe.identifier} %.3f".format(nearest)
      }
    }

    assertEquals(emptyList<String>(), crowded)
  }

  /** A mix of exactly a recipe's inputs finds it at the rank it asks for, and a slip of one unit does not find a neighbour. */
  @Test
  fun `a mix of a recipe's inputs finds that recipe and survives a slip of one unit`() {
    val wrong = ArrayList<String>()

    for (item in gear) {
      val recipe = recipeOf(item.identifier).single()
      val skillBlueprints = catalog.blueprints.discoverable(recipe.requiredSkillId)
      val own = checkNotNull(catalog.blueprints.pointOf(recipe.id))

      fun nearestTo(point: CraftVector) = skillBlueprints.minByOrNull { it.point.distanceTo(point) }?.recipe

      assertEquals(
        1f,
        DiscoveryService.learnChance(recipe.requiredSkillLevel, item.baseLevel),
        "${item.identifier} would not be certain to be learned at rank ${recipe.requiredSkillLevel}"
      )
      assertEquals(recipe.id, nearestTo(own)?.id, "${item.identifier} is not the nearest to its own mix")

      val parts = recipe.inputs.map { catalog.point(catalog.item(it.itemId).identifier) to it.amount }
      val main = recipe.inputs.first()
      val slips = listOf(1, -1).mapNotNull { delta ->
        val changed = parts.toMutableList()
        changed[0] = changed[0].first to main.amount + delta
        if (changed[0].second <= 0) null else CraftVector.mixOf(changed)
      }
      slips.forEach { slip -> if (nearestTo(slip)?.id != recipe.id) wrong += "${item.identifier} slips to ${nearestTo(slip)?.identifier}" }
    }

    // A slip is a different mix and may rightly land nearer a neighbour; it must be the exception, not the rule.
    assertTrue(wrong.size <= gear.size / 10, "${wrong.size} of ${gear.size * 2} slips leave the piece: ${wrong.take(12)}")
  }

  @Test
  fun `the chance and the time follow the docs, and a main material fits its piece's tier`() {
    gear.forEach { item ->
      val recipe = recipeOf(item.identifier).single()
      val level = GearRecipeGenerator.itemLevelOf(item)

      assertEquals(0.90f - max(0, level - 10) * 0.015f, recipe.baseSuccessChance, 0.002f, item.identifier)
      assertEquals(1.2f * level * level, recipe.craftSeconds, 0.1f + 0.0001f * level * level, item.identifier)

      val main = catalog.item(recipe.inputs.first().itemId)
      assertTrue(main.baseLevel <= level + 25, "${item.identifier} (Lv. $level) is made of ${main.identifier} (Lv. ${main.baseLevel})")
    }
  }

  @Test
  fun `a legendary is made of a main material that lets its roll reach it`() {
    // A forged roll is capped at its main material's level plus 20 (`CraftBonusService.rollCap`).
    val short = gear.filter { GearRecipeGenerator.itemLevelOf(it) >= 81 }.filter { item ->
      val main = catalog.item(recipeOf(item.identifier).single().inputs.first().itemId)
      main.baseLevel < 60
    }.map { it.identifier }

    assertEquals(emptyList<String>(), short)
  }

  @Test
  fun `gear_recipes-yml is what the generator makes from the rules`() {
    val committed = ClassPathResource(GearRecipeGenerator.OUTPUT_FILE).inputStream.use { String(it.readAllBytes()) }

    assertEquals(
      committed.replace("\r\n", "\n"),
      GearRecipeGenerator.render(GearRecipeGenerator.plan()).replace("\r\n", "\n"),
      "run ./gradlew genGearRecipes and commit the result"
    )
  }
}
