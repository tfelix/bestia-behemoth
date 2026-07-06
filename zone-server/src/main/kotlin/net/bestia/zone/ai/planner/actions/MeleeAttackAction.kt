package net.bestia.zone.ai.planner.actions

import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.behavior.Sequence
import net.bestia.zone.ai.behavior.leaves.InMeleeRangeLeaf
import net.bestia.zone.ai.behavior.leaves.MeleeAttackLeaf
import net.bestia.zone.ai.planner.GoapAction
import net.bestia.zone.ai.planner.StateKey
import org.springframework.stereotype.Component

@Component
class MeleeAttackAction : GoapAction {
  override val id = "melee_attack"
  override val preconditions = mapOf(
    StateKey.HAS_TARGET to true,
    StateKey.TARGET_IN_MELEE_RANGE to true
  )
  override val effects = mapOf(StateKey.TARGET_DEAD to true)
  override val cost = 1.0

  override fun behaviorTree(): BtNode = Sequence(InMeleeRangeLeaf(), MeleeAttackLeaf())
}
