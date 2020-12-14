package net.bestia.ai.behavior.tree

import net.bestia.ai.blackboard.Blackboard

class HasProperty(
    private val blackboard: Blackboard,
    private val property: String,
    private val compFn: (Float) -> Boolean
) : Node {
  override fun tick(): NodeStatus {
    val state = blackboard.getEntry(property) as Blackboard.Entry<Float>?
        ?: return NodeStatus.FAILED

    return if (compFn(state.data)) {
      NodeStatus.SUCCESS
    } else {
      NodeStatus.FAILED
    }
  }
}

