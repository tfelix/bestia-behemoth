package net.bestia.zone.ai.planner.actions

import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.behavior.leaves.WanderLeaf
import net.bestia.zone.ai.planner.GoapAction
import net.bestia.zone.ai.planner.StateKey
import org.springframework.stereotype.Component

@Component
class WanderAction : GoapAction {
  override val id = "wander"
  override val preconditions = emptyMap<StateKey, Boolean>()
  override val effects = mapOf(StateKey.AT_WANDER_POINT to true)
  override val cost = 1.0

  override fun behaviorTree(): BtNode = WanderLeaf()
}
