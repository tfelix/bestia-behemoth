package net.bestia.zone.battle.buff

import net.bestia.zone.ecs.battle.Damage
import net.bestia.zone.ecs.battle.buff.ActiveBuff
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * A pluggable reaction to a [BuffTriggerEvent]. Each concrete action implements its own logic, so
 * adding a new one never touches a central dispatch `when` in the system that invokes it (see
 * [net.bestia.zone.ecs.battle.buff.BuffDamageInterceptSystem]).
 */
sealed interface BuffTriggerAction {
  /**
   * Reacts to [incoming] damage about to be applied to [targetId] carrying [buffInstance].
   * Returns the amount of damage from [incoming] that still applies to the target after this
   * action ran (0 fully negates it). May schedule side effects (e.g. damage onto the attacker)
   * via [world].
   */
  fun apply(world: World, buffInstance: ActiveBuff, targetId: EntityId, incoming: Damage.DamageAmount): Int

  /**
   * Reflects [percent] of the incoming hit back onto its source as a new, already-marked-reflected
   * [Damage] entry (see [Damage.DamageAmount.isReflected] for why re-reflection is prevented), and
   * reduces the damage the original target actually takes by the same amount.
   */
  data class ReflectDamage(val percent: Double) : BuffTriggerAction {
    override fun apply(world: World, buffInstance: ActiveBuff, targetId: EntityId, incoming: Damage.DamageAmount): Int {
      val reflected = (incoming.amount * percent).toInt().coerceIn(0, incoming.amount)
      if (reflected > 0) {
        world.update(incoming.sourceEntityId, default = { Damage() }) {
          it.add(reflected, targetId, isReflected = true)
        }
      }
      return incoming.amount - reflected
    }
  }
}
