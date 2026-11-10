package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.InCombat
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.util.EntityId

/**
 * Raises the dead where they lie, as Resurrection does: the counterpart of a respawn at the save point. The body
 * stands up with a share of its health and the death screen closes, since the `Dead` component it reads is gone.
 */
object Revival {

  fun revive(world: World, entityId: EntityId, healthPercent: Int): Boolean {
    if (!world.has(entityId, Dead::class)) return false
    val health = world.get(entityId, Health::class) ?: return false

    health.current = (health.max * healthPercent / 100).coerceAtLeast(1)
    world.remove(entityId, Dead::class)
    world.remove(entityId, TakenDamage::class)
    world.remove(entityId, InCombat::class)

    return true
  }
}
