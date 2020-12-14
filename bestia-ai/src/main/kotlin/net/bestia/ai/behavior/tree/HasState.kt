package net.bestia.ai.behavior.tree

import net.bestia.ai.blackboard.Blackboard

class HasState(
    private val state: String,
    private val blackboard: Blackboard
) : Node {
  override fun tick(): NodeStatus {
    val state = blackboard.getEntry("state/$state") as Blackboard.Entry<Boolean>?

    return if (state == null || !state.data) {
      NodeStatus.SUCCESS
    } else {
      NodeStatus.FAILED
    }
  }
}