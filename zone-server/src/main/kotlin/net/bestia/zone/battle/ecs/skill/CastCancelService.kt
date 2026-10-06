package net.bestia.zone.battle.ecs.skill

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Single entry point for aborting a running cast or craft. Removing the component is what both stops the
 * countdown and notifies the client (via the generic component-removed message), so every "the channel got
 * interrupted" path funnels through here. No-op when nothing is running.
 *
 * The two channels are separate components and one bar: [Crafting] deliberately emits the same
 * [CastingComponentSMSG] a cast does, so anything that ends one has to be able to end the other, and
 * `CraftItemHandler` / `ActivateSkillHandler` each cancel the opposite before starting their own.
 */
@Service
class CastCancelService {

  fun cancelCast(world: World, entityId: EntityId) {
    world.remove(entityId, Casting::class)
  }

  fun cancelCraft(world: World, entityId: EntityId) {
    world.remove(entityId, Crafting::class)
  }
}
