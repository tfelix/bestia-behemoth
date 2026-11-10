package net.bestia.zone.battle.attack

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * Something that may happen when a basic swing lands: Triple Blow's extra blows, a poisoned blade's sting. Each one
 * is a bean; [AttackExecutionService] asks every one after a swing connected. Runs inside the swing's deferred
 * block, so it may stage damage and apply statuses directly.
 */
interface SwingProc {
  fun onSwingLanded(world: World, attackerId: EntityId, targetId: EntityId, swing: Damage)
}
