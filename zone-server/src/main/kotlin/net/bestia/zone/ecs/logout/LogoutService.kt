package net.bestia.zone.ecs.logout

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Single entry point for cancelling a pending logout. Removing the [LogoutIntent] component is what
 * both stops the countdown and notifies the client (via the generic component-removed message), so
 * every "the player did something" cancel path funnels through here. No-op when nothing is pending.
 *
 * Safe to call from off-tick message handlers and from inside a system's tick alike — [WorldView]
 * serialises the mutation on the world lock either way.
 */
@Service
class LogoutService(
  private val world: WorldView,
) {

  fun cancelLogout(entityId: EntityId) {
    world.modify(entityId) { id ->
      if (get(id, LogoutIntent::class) != null) {
        remove(id, LogoutIntent::class)
      }
    }
  }
}
