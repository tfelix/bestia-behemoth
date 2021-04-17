package net.bestia.ai.planner.goap.effect

import net.bestia.ai.firstIsInstanceOrNull
import net.bestia.ai.planner.goap.condition.Condition
import net.bestia.ai.planner.goap.condition.IsAtPosition

data class SetPosition(
    private val x: Long,
    private val y: Long,
    private val z: Long
) : Effect {
  override fun apply(states: Set<Condition>): Set<Condition> {
    return when (val state = states.firstIsInstanceOrNull(IsAtPosition::class.java)) {
      null -> states + IsAtPosition(x, y, z)
      else -> states - state + IsAtPosition(x, y, z)
    }
  }
}