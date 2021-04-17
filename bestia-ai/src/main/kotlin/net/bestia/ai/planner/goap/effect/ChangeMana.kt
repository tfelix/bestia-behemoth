package net.bestia.ai.planner.goap.effect

import net.bestia.ai.firstIsInstanceOrNull
import net.bestia.ai.planner.goap.condition.Condition
import net.bestia.ai.planner.goap.condition.HasMana

data class ChangeMana(
    private val amount: Int
) : Effect {
  override fun apply(states: Set<Condition>): Set<Condition> {
    return when (val state = states.firstIsInstanceOrNull(HasMana::class.java)) {
      null -> states + HasMana(amount)
      else -> states - state + state.copy(amount = state.amount + amount)
    }
  }
}