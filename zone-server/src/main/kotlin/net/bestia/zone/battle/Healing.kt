package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * Where a heal from a skill, an item or a status effect meets health, so one modifier weighs every one of them.
 * Natural regeneration is not a heal and does not come through here.
 */
object Healing {

  /** Returns the health actually restored after the target's heal-received modifier. */
  fun heal(world: World, targetId: EntityId, amount: Int): Int {
    val health = world.get(targetId, Health::class) ?: return 0
    val received = receivedAmount(world, targetId, amount)

    val before = health.current
    // CurMax.current clamps to [0, max] itself.
    health.current += received

    return health.current - before
  }

  fun receivedAmount(world: World, targetId: EntityId, amount: Int): Int {
    val percent = world.get(targetId, CombatStats::class)?.values?.healReceivedPercent ?: 0

    return (amount * (100 + percent) / 100).coerceAtLeast(0)
  }
}
