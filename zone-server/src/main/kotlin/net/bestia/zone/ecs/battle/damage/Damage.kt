package net.bestia.zone.ecs.battle.damage

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

class Damage() : Component {

  val amounts: MutableList<DamageAmount> = mutableListOf()

  data class DamageAmount(
    val amount: Int,
    val sourceEntityId: EntityId,
    /**
     * True if this amount is itself the result of a reflect trigger effect. Lets
     * [net.bestia.zone.ecs.battle.effects.StatusEffectDamageInterceptSystem] skip re-triggering
     * reflect effects on damage that was already reflected once, so two reflectors can't bounce
     * the same hit back and forth forever.
     */
    val isReflected: Boolean = false,
  )

  fun add(amount: Int, sourceEntity: EntityId, isReflected: Boolean = false) {
    amounts.add(DamageAmount(amount, sourceEntity, isReflected))
  }

  fun total(): Int {
    return amounts.sumOf { it.amount }
  }
}
