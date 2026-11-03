package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * Soaks damage from a pool sized on the bearer's health, for a few hits at most (Ragnarok Online's Kyrie
 * Eleison). Pool and hits left live in the instance's `amount` and `count`.
 */
@Component
class Barrier : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 120.0
  }

  override fun onApply(context: StatusEffectHookContext) {
    val maxHealth = context.world.get(context.hostId, Health::class)?.max ?: 0
    val level = context.effect.level

    context.effect.amount = maxHealth * (BASE_POOL_PERCENT + POOL_PERCENT_PER_LEVEL * level) / 100
    context.effect.count = BASE_HITS + level / 2
  }

  override fun onDamageTaken(context: StatusEffectHookContext, amount: Int): Int {
    if (amount <= 0) return amount

    val soaked = minOf(amount, context.effect.amount)
    context.effect.amount -= soaked
    context.effect.count -= 1
    if (context.effect.amount <= 0 || context.effect.count <= 0) {
      context.removeSelf()
    }

    return amount - soaked
  }

  private companion object {
    const val BASE_POOL_PERCENT = 15
    const val POOL_PERCENT_PER_LEVEL = 2
    const val BASE_HITS = 3
  }
}
