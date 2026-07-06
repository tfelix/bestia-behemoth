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
      when (child.tick(context)) {
        Status.SUCCESS -> continue
        Status.RUNNING -> return Status.RUNNING
        Status.FAILURE -> return Status.FAILURE
      }
    }
    return Status.SUCCESS
  }
}

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
      when (child.tick(context)) {
        Status.SUCCESS -> return Status.SUCCESS
        Status.RUNNING -> return Status.RUNNING
        Status.FAILURE -> continue
      }
    }
    return Status.FAILURE
  }
}
