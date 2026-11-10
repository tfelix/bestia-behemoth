package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Whether a blow or a blast may push [entityId] back. A dash or a leap of its own is not a knockback. */
object Knockback {

  fun isImmune(world: World, entityId: EntityId): Boolean {
    return world.get(entityId, CombatStats::class)?.values?.knockbackImmune == true
  }
}
