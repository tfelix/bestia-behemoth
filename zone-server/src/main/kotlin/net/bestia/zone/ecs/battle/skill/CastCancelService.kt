package net.bestia.zone.ecs.battle.skill

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.crafting.Crafting
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
 *
 * Note this is for callers *outside* the tick (message handlers). Systems already hold the world lock
 * and must remove the component directly instead - see
 * [net.bestia.zone.ecs.battle.damage.ReceivedDamageSystem].
 */
@Service
class CastCancelService(
  private val world: WorldView,
) {

  fun cancelCast(entityId: EntityId) {
    world.modify(entityId) { id ->
      if (get(id, Casting::class) != null) {
        remove(id, Casting::class)
      }
    }
  }

  fun cancelCraft(entityId: EntityId) {
    world.modify(entityId) { id ->
      if (get(id, Crafting::class) != null) {
        remove(id, Crafting::class)
      }
    }
  }
}
