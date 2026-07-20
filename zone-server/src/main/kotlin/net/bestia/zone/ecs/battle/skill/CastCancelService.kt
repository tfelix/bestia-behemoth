package net.bestia.zone.ecs.battle.skill

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Single entry point for aborting a running cast. Removing the [Casting] component is what both stops
 * the countdown and notifies the client (via the generic component-removed message), so every
 * "the cast got interrupted" path funnels through here. No-op when nothing is being cast.
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
}
