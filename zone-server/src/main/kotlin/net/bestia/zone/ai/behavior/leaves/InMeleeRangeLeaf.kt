package net.bestia.zone.ai.behavior.leaves

import net.bestia.zone.ai.behavior.BtContext
import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.behavior.Locomotion
import net.bestia.zone.ai.behavior.Status

/**
 * Condition leaf: SUCCESS when the target is within melee range, FAILURE otherwise (including when
 * there is no known target).
 */
class InMeleeRangeLeaf : BtNode {
  override fun tick(context: BtContext): Status {
    val targetPos = context.brain.targetPosition ?: return Status.FAILURE

    return if (Locomotion.distanceTo(context.world, context.entityId, targetPos) <= context.brain.meleeRange) {
      Status.SUCCESS
    } else {
      Status.FAILURE
    }
  }
}
