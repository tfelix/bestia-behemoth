package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Whether a blow or a blast may push [entityId] back. A dash or a leap of its own is not a knockback. */
object Knockback {

  fun isImmune(world: World, entityId: EntityId): Boolean {
    return world.get(entityId, CombatStats::class)?.values?.knockbackImmune == true
  }

  /** How far a push of [tiles] moves [entityId]: not at all when immune, half as far while it stays steady. */
  fun tilesFor(world: World, entityId: EntityId, tiles: Int): Int {
    return when {
      isImmune(world, entityId) -> 0
      SteadyAboveHalfHealth.holds(world, entityId) -> tiles / 2
      else -> tiles
    }
  }
}
