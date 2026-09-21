package net.bestia.zone.ecs.battle.attack

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Drops an entity's standing attack order. No-op when it has none.
 *
 * For callers *outside* the tick (message handlers, session teardown); a system already holds the world lock
 * and removes the component directly - see [AttackSystem].
 */
@Service
class AttackCancelService(
  private val world: WorldView,
) {

  fun cancelAttack(entityId: EntityId) {
    world.modify(entityId) { id ->
      if (get(id, AttackTarget::class) != null) {
        remove(id, AttackTarget::class)
      }
    }
  }
}
