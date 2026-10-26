package net.bestia.zone.crafting.discovery

import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.StarterKit
import org.springframework.stereotype.Service

/**
 * Where each blueprint sits in the feature space: the mix of its own recipe's inputs. Derived rather than written
 * down, so a recipe and its point cannot drift apart, and the known recipe always lands exactly on it.
 *
 * A producing recipe with an input that has no `craft-axes` has no point, and so cannot be discovered.
 */
@Service
class BlueprintPoints {

  data class Blueprint(val recipe: Recipe, val point: CraftVector)

  private var byRecipeId: Map<Long, Blueprint> = emptyMap()
  private var discoverableBySkill: Map<Long, List<Blueprint>> = emptyMap()

  fun load(recipes: Collection<Recipe>, axes: MaterialAxes, starterKit: StarterKit) {
    val blueprints = recipes
      .filter { it.effect == RecipeEffect.PRODUCE }
      .mapNotNull { recipe -> pointOf(recipe, axes)?.let { Blueprint(recipe, it) } }

    blueprints.groupBy { it.recipe.requiredSkillId }.values.forEach { requireDistinct(it) }

    byRecipeId = blueprints.associateBy { it.recipe.id }
    discoverableBySkill = blueprints
      .filterNot { starterKit.isStarterBlueprint(it.recipe.id) }
      .groupBy { it.recipe.requiredSkillId }
  }

  fun pointOf(recipeId: Long): CraftVector? = byRecipeId[recipeId]?.point

  /** The blueprints of one skill that are not in the starter kit, whether a given master knows them or not. */
  fun discoverable(skillId: Long): List<Blueprint> = discoverableBySkill[skillId] ?: emptyList()

  private fun pointOf(recipe: Recipe, axes: MaterialAxes): CraftVector? {
    val parts = recipe.inputs.map { input -> (axes.of(input.itemId) ?: return null) to input.amount }

    return if (parts.isEmpty()) null else CraftVector.mixOf(parts)
  }

  /** Two blueprints on one point could never be told apart by any mix. */
  private fun requireDistinct(sameSkill: List<Blueprint>) {
    sameSkill.forEachIndexed { i, a ->
      sameSkill.drop(i + 1).forEach { b ->
        require(a.point.distanceTo(b.point) > MIN_DISTANCE) {
          "Recipes ${a.recipe.identifier} and ${b.recipe.identifier} have the same inputs in the same proportion"
        }
      }
    }
  }

  private companion object {
    const val MIN_DISTANCE = 1e-6
  }
}
