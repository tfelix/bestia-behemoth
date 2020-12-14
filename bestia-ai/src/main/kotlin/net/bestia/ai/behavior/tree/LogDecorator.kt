package net.bestia.ai.behavior.tree

class LogDecorator(
    private val node: Node,
    private val history: CallHistory
) : Node {

  class CallHistory {
    var calls: MutableList<String> = mutableListOf()
  }

  override fun tick(): NodeStatus {
    val cr = node.tick()
    history.calls.add("Called: ${node.javaClass.simpleName}: $cr")

    return cr
  }
}