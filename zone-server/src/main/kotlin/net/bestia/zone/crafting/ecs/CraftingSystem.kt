package net.bestia.zone.crafting.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent
import net.bestia.zone.battle.ecs.skill.Crafting

/**
 * Drives the craft countdown and resolves each craft on the tick it elapses.
 *
 * Structurally the same as [net.bestia.zone.casting.ecs.CastingSystem], and ordered right after it
 * for the same reason it sits where it does: dropping the component is what tells the client the bar is
 * done, and an interrupt produces exactly the same signal, because visually both just end.
 *
 * Interruption is not handled here - it happens by removing the component elsewhere, from
 * [net.bestia.zone.battle.ecs.skill.CastCancelService] for message handlers and from
 * [net.bestia.zone.battle.ecs.damage.ReceivedDamageSystem] for damage.
 */
@SpringComponent
class CraftingSystem(
  private val craftingService: CraftingService,
) : System {
  override val phase = Phase.ACTIONS

  override val writes: ComponentClassSet = setOf(Crafting::class)

  override fun update(world: World, deltaTime: Float) {
    // Collected first so the removals below do not mutate what is being iterated.
    var completed: MutableList<Pair<EntityId, Crafting>>? = null

    world.query(Crafting::class).each { id ->
      val crafting = get<Crafting>()
      crafting.countdown(deltaTime)

      if (crafting.hasElapsed()) {
        (completed ?: mutableListOf<Pair<EntityId, Crafting>>().also { completed = it }).add(id to crafting)
      }
    }

    completed?.forEach { (id, crafting) ->
      LOG.debug { "Craft of recipe ${crafting.recipeId} by entity $id finished" }

      world.remove(id, Crafting::class)
      craftingService.resolve(world, id, crafting)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
