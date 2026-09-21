package net.bestia.zone.ecs.battle.attack

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

/**
 * A standing order to keep swinging at [targetEntityId]. [AttackSystem] carries it out; [AttackCancelService]
 * and the death and respawn paths clear it.
 *
 * Server-side bookkeeping only - deliberately not [net.bestia.zone.ecs.core.Dirtyable]. The client has no
 * notion of an attack order and infers a fight from the damage it sees.
 */
class AttackTarget(
  var targetEntityId: EntityId
) : Component
