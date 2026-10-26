package net.bestia.zone.crafting.ecs

import net.bestia.zone.ecs.core.Component

/**
 * The blueprints a master has learned, read on the tick when they craft. Loaded at spawn from
 * [net.bestia.zone.crafting.KnownRecipeStore]; starter blueprints are not in it.
 */
class KnownRecipes(
  private val recipeIds: MutableSet<Long> = mutableSetOf()
) : Component {

  fun knows(recipeId: Long): Boolean = recipeId in recipeIds

  fun learn(recipeId: Long) {
    recipeIds += recipeId
  }
}
