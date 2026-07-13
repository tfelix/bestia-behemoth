package net.bestia.zone.ai.behavior

/**
 * Runs children in order. Fails on the first child that fails, yields RUNNING on the first child
 * that is running, and succeeds only when every child has succeeded.
 */
class Sequence(
  private val children: List<BtNode>
) : BtNode {

  constructor(vararg children: BtNode) : this(children.toList())

  override fun tick(context: BtContext): Status {
    for (child in children) {
      return when (child.tick(context)) {
        Status.SUCCESS -> continue
        Status.RUNNING -> Status.RUNNING
        Status.FAILURE -> Status.FAILURE
      }
    }
    return Status.SUCCESS
  }
}
