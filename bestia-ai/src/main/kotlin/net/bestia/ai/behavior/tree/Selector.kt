package net.bestia.ai.behavior.tree

class Selector(
    private val childs: List<Node>
) : Node {
  override fun tick(): NodeStatus {
    for (child in childs) {
      val cr = child.tick()

      if (cr == NodeStatus.SUCCESS || cr == NodeStatus.RUNNING) {
        return cr
      }
    }

    return NodeStatus.FAILED
  }
}


