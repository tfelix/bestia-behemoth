package net.bestia.zone.ai.goal.goals

import net.bestia.zone.ai.goal.Goal
import net.bestia.zone.ai.planner.StateKey
import net.bestia.zone.ai.planner.WorldState
import org.springframework.stereotype.Component

@Component
class KillEnemyGoal : Goal {
  override val name = "kill_enemy"
  override val desiredState = WorldState.of(StateKey.TARGET_DEAD to true)
}
