package net.bestia.zone.ai.behavior.leaves

import net.bestia.zone.ai.behavior.BtContext
import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.behavior.Locomotion
import net.bestia.zone.ai.behavior.Status

/**
 * Runs away from the nearest perceived threat. SUCCESS once no hostile is in sight (safe again),
 * RUNNING while still fleeing.
 */
class FleeLeaf : BtNode {
  override fun tick(context: BtContext): Status {
    val snapshot = context.brain.latestPercept
    val threat = context.brain.threatPosition

    if (threat == null || snapshot == null || snapshot.hostiles.isEmpty()) {
      return Status.SUCCESS
    }

    // Cornered counts as failure: there is no way further from the threat, so fleeing is not an option and
    // the think stage should be given the chance to pick fighting instead of leaving the creature shuffling
    // against a wall.
    return if (Locomotion.stepAwayFrom(context, threat)) Status.RUNNING else Status.FAILURE
  }
}
