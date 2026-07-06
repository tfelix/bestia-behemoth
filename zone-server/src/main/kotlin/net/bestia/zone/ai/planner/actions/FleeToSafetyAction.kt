package net.bestia.zone.ai.planner.actions

import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.behavior.leaves.FleeLeaf
import net.bestia.zone.ai.planner.GoapAction
import net.bestia.zone.ai.planner.StateKey
import org.springframework.stereotype.Component

@Component
class FleeToSafetyAction : GoapAction {
  override val id = "flee_to_safety"
  override val preconditions = emptyMap<StateKey, Boolean>()
  override val effects = mapOf(StateKey.SELF_SAFE to true)
  override val cost = 1.0

  override fun behaviorTree(): BtNode = FleeLeaf()
}
