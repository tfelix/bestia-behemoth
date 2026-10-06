package net.bestia.zone.casting

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Tells a crafter what they can make with the skill they just cast. The crafting slice implements it. */
interface RecipeOffering {

  fun offerRecipes(world: World, casterId: EntityId, skillId: Long)
}
