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
    Locomotion.wanderStep(context.entity)
    return Status.RUNNING
  }
}
