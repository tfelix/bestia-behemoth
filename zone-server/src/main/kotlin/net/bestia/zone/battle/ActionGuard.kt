package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Refuses what a stun, a freeze or a fear forbids. Silent like [net.bestia.zone.entity.ecs.DeadActionGuard]:
 * the client shows the effect, so a request that arrives anyway needs no answer.
 */
@Service
class ActionGuard {

  fun refuses(world: World, entityId: EntityId, lock: ActionLock): Boolean {
    return world.get(entityId, StatusEffects::class)?.isLocked(lock) ?: false
  }
}
