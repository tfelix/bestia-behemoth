package net.bestia.zone.crafting

import net.bestia.zone.crafting.ecs.KnownRecipes
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Service

/**
 * Who works, and whose knowledge they work with. A master crafts alone; a bestia crafts with its master's skills
 * and blueprints but its own hands, bag and container ([Crafter.playerBestiaId]).
 */
@Service
class Crafters(
  private val starterKit: StarterKit,
) {

  inner class Crafter(
    val master: Master,
    val playerBestiaId: PlayerBestiaId?,
    val skills: KnownSkills?,
    private val blueprints: KnownRecipes?,
  ) {
    /**
     * Whether the master knows how to make what [recipe] makes: from the starter kit, or learned. Repairing,
     * upgrading and cutting a slot are things a skill does to an item, so they need no blueprint.
     */
    fun knowsBlueprint(recipe: Recipe): Boolean {
      if (recipe.needsTarget || starterKit.isStarterBlueprint(recipe.id)) return true

      return blueprints?.knows(recipe.id) == true
    }
  }

  /** Null when [crafterId] is neither the master at [masterEntityId] nor one of its bestias. */
  fun of(world: World, crafterId: EntityId, masterEntityId: EntityId): Crafter? {
    val master = world.get(masterEntityId, Master::class) ?: return null
    val playerBestiaId = if (crafterId == masterEntityId) {
      null
    } else {
      val owned = world.get(crafterId, OwnedBestia::class)?.takeIf { it.masterId == master.masterId } ?: return null
      owned.playerBestiaId
    }

    return Crafter(
      master = master,
      playerBestiaId = playerBestiaId,
      skills = world.get(masterEntityId, KnownSkills::class),
      blueprints = world.get(masterEntityId, KnownRecipes::class),
    )
  }
}
