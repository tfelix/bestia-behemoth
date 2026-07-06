package net.bestia.zone.ai.behavior.leaves

import net.bestia.zone.ai.behavior.BtContext
import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.behavior.Locomotion
import net.bestia.zone.ai.behavior.Status

/**
 * Moves the NPC toward its target's last known position. RUNNING while closing the gap, SUCCESS once
 * within melee range, FAILURE when there is no known target position.
 */
class MoveToTargetLeaf : BtNode {
  override fun tick(context: BtContext): Status {
    val targetPos = context.brain.targetPosition ?: return Status.FAILURE

    if (Locomotion.distanceTo(context.entity, targetPos) <= context.brain.meleeRange) {
      return Status.SUCCESS
    }

    Locomotion.stepToward(context.entity, targetPos)
    return Status.RUNNING
  }
}
