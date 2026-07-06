package net.bestia.zone.ai.planner.actions

import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.behavior.leaves.MoveToTargetLeaf
import net.bestia.zone.ai.planner.GoapAction
import net.bestia.zone.ai.planner.StateKey
import org.springframework.stereotype.Component

@Component
class ApproachTargetAction : GoapAction {
  override val id = "approach_target"
  override val preconditions = mapOf(StateKey.HAS_TARGET to true)
  override val effects = mapOf(StateKey.TARGET_IN_MELEE_RANGE to true)
  override val cost = 2.0

  override fun behaviorTree(): BtNode = MoveToTargetLeaf()
}
