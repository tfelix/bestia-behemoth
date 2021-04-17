package net.bestia.ai.planner.goap.effect

import net.bestia.ai.firstIsInstanceOrNull
import net.bestia.ai.planner.goap.condition.Condition
import net.bestia.ai.planner.goap.condition.HasHealth

data class AddHealth(
    private val amount: Int
) : Effect {
  override fun apply(states: Set<Condition>): Set<Condition> {
    return when (val state = states.firstIsInstanceOrNull(HasHealth::class.java)) {
      null -> states + HasHealth(amount)
      else -> states - state + state.copy(amount = state.amount + amount)
    }
  }
}