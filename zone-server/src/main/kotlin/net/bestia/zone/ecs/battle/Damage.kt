package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

class Damage() : Component {

  val amounts: MutableList<DamageAmount> = mutableListOf()

  data class DamageAmount(
    val amount: Int,
    val sourceEntityId: EntityId,
    /**
     * True for damage created by reflecting a hit back onto its source (see
     * `net.bestia.zone.battle.buff.BuffTriggerAction.ReflectDamage`). Prevents a pair of reflect
     * buffs from bouncing damage back and forth forever - only non-reflected amounts are
     * re-evaluated against trigger effects.
     */
    val isReflected: Boolean = false
  )

  fun add(amount: Int, sourceEntity: EntityId, isReflected: Boolean = false) {
    amounts.add(DamageAmount(amount, sourceEntity, isReflected))
  }

  fun total(): Int {
    return amounts.sumOf { it.amount }
  }
}
