package net.bestia.ai.behavior.tree

import net.bestia.ai.blackboard.Blackboard

class PlanAction(
    private val blackboard: Blackboard,
    private val action: String
) : Node {
  override fun tick(): NodeStatus {
    val action = blackboard.getOrDefaultEntry("action", "") as Blackboard.Entry<String>

    action.data = this.action
    blackboard.setEntry(action)

    return NodeStatus.RUNNING
  }
}