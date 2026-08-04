package net.bestia.zone.ai.behavior.leaves

import net.bestia.zone.ai.behavior.BtContext
import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.behavior.Locomotion
import net.bestia.zone.ai.behavior.Status

/**
 * Idle random wander (ports the old TestAiSystem behaviour). Picks a fresh random adjacent tile
 * whenever the NPC is standing still, producing a continuous random walk. Always RUNNING: wandering
 * is an open-ended activity that the think stage preempts as soon as a higher-utility goal (e.g.
 * chasing an enemy) wins.
 */
class WanderLeaf : BtNode {
  override fun tick(context: BtContext): Status {
    // Still always RUNNING even when no step was available. A creature penned in by terrain has nowhere to
    // amble to, and that is not a failed action - wandering is what it does while nothing better applies, and
    // reporting FAILURE would make the think stage replan every tick for as long as it stayed hemmed in.
    Locomotion.wanderStep(context, context.brain.homePosition, context.brain.wanderRadius)
    return Status.RUNNING
  }
}
