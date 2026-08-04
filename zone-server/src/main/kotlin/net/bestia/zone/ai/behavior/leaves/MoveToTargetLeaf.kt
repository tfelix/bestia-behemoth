package net.bestia.zone.ai.behavior.leaves

import net.bestia.zone.ai.behavior.BtContext
import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.behavior.Locomotion
import net.bestia.zone.ai.behavior.Status

/**
 * Moves the NPC toward its target's last known position. RUNNING while closing the gap, SUCCESS once
 * within melee range, FAILURE when there is no known target position or no way to reach it.
 */
class MoveToTargetLeaf : BtNode {
  override fun tick(context: BtContext): Status {
    val targetPos = context.brain.targetPosition ?: return Status.FAILURE

    if (Locomotion.distanceTo(context.world, context.entityId, targetPos) <= context.brain.meleeRange) {
      return Status.SUCCESS
    }

    // Failing rather than reporting RUNNING forever is new, and it is the point of pathfinding returning a
    // verdict at all: a target on the far side of a river is genuinely unreachable, and an action that stays
    // RUNNING would keep the plan alive while the creature stood still. FAILURE lets the think stage pick
    // something else to do.
    return if (Locomotion.stepToward(context, targetPos)) Status.RUNNING else Status.FAILURE
  }
}
