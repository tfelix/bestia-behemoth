package net.bestia.ai.behavior.tree

class Sequence(
    private val childs: List<Node>
) : Node {
  override fun tick(): NodeStatus {
    for (child in childs) {
      val cr = child.tick()

      if (cr == NodeStatus.SUCCESS) {
        continue
      }

      return cr
    }

    return NodeStatus.SUCCESS
  }
}

