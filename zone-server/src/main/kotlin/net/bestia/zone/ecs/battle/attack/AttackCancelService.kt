package net.bestia.zone.ecs.battle.attack

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/** Drops an entity's standing attack order. No-op when it has none. */
@Service
class AttackCancelService {

  fun cancelAttack(world: World, entityId: EntityId) {
    world.remove(entityId, AttackTarget::class)
  }
}
