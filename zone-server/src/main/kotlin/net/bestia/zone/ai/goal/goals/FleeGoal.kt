package net.bestia.zone.ai.goal.goals

import net.bestia.zone.ai.goal.Goal
import net.bestia.zone.ai.planner.StateKey
import net.bestia.zone.ai.planner.WorldState
import org.springframework.stereotype.Component

@Component
class FleeGoal : Goal {
  override val name = "flee"
  override val desiredState = WorldState.of(StateKey.SELF_SAFE to true)
}
