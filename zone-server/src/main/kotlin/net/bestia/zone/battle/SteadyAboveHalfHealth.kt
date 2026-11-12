package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Unbreakable from Lv.2: while above half its HP, a stun and a knockback reach the Brawler at half strength. */
object SteadyAboveHalfHealth {

  fun holds(world: World, entityId: EntityId): Boolean {
    if (world.get(entityId, CombatStats::class)?.values?.halvesStunAndKnockback != true) return false
    val health = world.get(entityId, Health::class) ?: return false

    return health.current * 2 > health.max
  }
}
