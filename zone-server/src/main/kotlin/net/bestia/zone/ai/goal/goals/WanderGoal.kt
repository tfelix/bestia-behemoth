package net.bestia.zone.ai.goal.goals

import net.bestia.zone.ai.goal.Goal
import net.bestia.zone.ai.planner.StateKey
import net.bestia.zone.ai.planner.WorldState
import org.springframework.stereotype.Component

@Component
class WanderGoal : Goal {
  override val name = "idle_wander"
  override val desiredState = WorldState.of(StateKey.AT_WANDER_POINT to true)
}
