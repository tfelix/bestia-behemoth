package net.bestia.ai.planner.goap.effect

import net.bestia.ai.planner.goap.condition.Condition

interface Effect {
  fun apply(states: Set<Condition>): Set<Condition>
}
