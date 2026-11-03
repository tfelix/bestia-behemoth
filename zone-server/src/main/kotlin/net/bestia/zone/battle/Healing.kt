package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * Where a heal from a skill, an item or a status effect meets health, so sickness halves every one of them and
 * any of them closes a bleeding wound. Natural regeneration is not a heal and does not come through here.
 */
object Healing {

  /** Returns the health actually restored after the target's heal-received modifier. */
  fun heal(world: World, targetId: EntityId, amount: Int): Int {
    val health = world.get(targetId, Health::class) ?: return 0
    val received = receivedAmount(world, targetId, amount)

    val before = health.current
    // CurMax.current clamps to [0, max] itself.
    health.current += received

    if (received > 0 && world.get(targetId, StatusEffects::class)?.removeEffect(StatusEffectId.BLEEDING.id) == true) {
      world.add(targetId, IsStatusValueDirty)
    }

    return health.current - before
  }

  fun receivedAmount(world: World, targetId: EntityId, amount: Int): Int {
    val percent = world.get(targetId, CombatStats::class)?.values?.healReceivedPercent ?: 0

    return (amount * (100 + percent) / 100).coerceAtLeast(0)
  }
}
