package net.bestia.zone.ai.behavior

/**
 * Runs children in order until one does not fail. Returns that child's SUCCESS/RUNNING; fails only
 * when every child fails.
 */
class Selector(
  private val children: List<BtNode>
) : BtNode {

  constructor(vararg children: BtNode) : this(children.toList())

  override fun tick(context: BtContext): Status {
    for (child in children) {
      return when (child.tick(context)) {
        Status.SUCCESS -> Status.SUCCESS
        Status.RUNNING -> Status.RUNNING
        Status.FAILURE -> continue
      }
    }
    return Status.FAILURE
  }
}