package net.bestia.zone.ai.bt

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status

/**
 * Runs [children] in order: the first FAILURE fails the whole sequence, the first RUNNING child
 * suspends it there, and it succeeds only once every child has succeeded.
 *
 * ### Reactive, not resuming
 *
 * Every tick re-ticks from the *first* child rather than resuming wherever it left off. That is
 * deliberate and is what makes `sequence { condition(inRange); run(strike) }` correct: the range
 * check is re-evaluated every tick, so a target walking away aborts the strike instead of the
 * sequence blindly continuing from the child that was running. The cost is that earlier children
 * must be cheap and idempotent — which, for condition checks, they are.
 *
 * A resuming variant would need a cursor and a rule for invalidating it; nothing in the AI needs one
 * yet, so it is deliberately absent rather than half-specified.
 *
 * Named `SequenceNode` rather than `Sequence` so it never shadows `kotlin.Sequence` for other code in
 * this package. Prefer building it through the `sequence { }` DSL.
 */
class SequenceNode(private val children: List<BtNode>) : BtNode {

  constructor(vararg children: BtNode) : this(children.toList())

  override fun tick(context: BtContext): Status {
    for (child in children) {
      when (val status = child.tick(context)) {
        Status.SUCCESS -> Unit // this child is done; fall through to the next one
        Status.RUNNING, Status.FAILURE -> return status
      }
    }
    return Status.SUCCESS
  }
}

/**
 * Runs [children] in order until one does *not* fail, and reports that child's SUCCESS or RUNNING.
 * Fails only when every child has failed. The behaviour-tree spelling of "try these in priority
 * order, take the first that works".
 *
 * Reactive in the same sense as [SequenceNode]: it re-evaluates from the highest-priority child every
 * tick, so a cheaper option becoming viable preempts a running fallback.
 */
class SelectorNode(private val children: List<BtNode>) : BtNode {

  constructor(vararg children: BtNode) : this(children.toList())

  override fun tick(context: BtContext): Status {
    for (child in children) {
      when (val status = child.tick(context)) {
        Status.FAILURE -> Unit // this child declined; try the next one
        Status.SUCCESS, Status.RUNNING -> return status
      }
    }
    return Status.FAILURE
  }
}

/** When a [ParallelNode] is considered done. */
enum class ParallelPolicy {
  /** Succeed once every child has succeeded; fail as soon as any child fails. */
  REQUIRE_ALL,

  /** Succeed as soon as any child succeeds; fail only once every child has failed. */
  REQUIRE_ONE,
}

/**
 * Ticks *every* child each tick and aggregates the results per [policy].
 *
 * Unlike [SequenceNode]/[SelectorNode] this does not short-circuit before ticking the rest, which is
 * the whole point: it is how one action drives two things at once — "walk to the target while
 * shouting an alert", or "keep fleeing while watching for a safe tile". Children must therefore be
 * genuinely independent; two children that both write `Path` will fight over it.
 */
class ParallelNode(
  private val children: List<BtNode>,
  private val policy: ParallelPolicy = ParallelPolicy.REQUIRE_ALL,
) : BtNode {

  override fun tick(context: BtContext): Status {
    var succeeded = 0
    var failed = 0

    // Every child is ticked even once the outcome is decided, so side effects stay symmetric
    // between ticks rather than depending on child order.
    for (child in children) {
      when (child.tick(context)) {
        Status.SUCCESS -> succeeded++
        Status.FAILURE -> failed++
        Status.RUNNING -> Unit
      }
    }

    return when (policy) {
      ParallelPolicy.REQUIRE_ALL -> when {
        failed > 0 -> Status.FAILURE
        succeeded == children.size -> Status.SUCCESS
        else -> Status.RUNNING
      }

      ParallelPolicy.REQUIRE_ONE -> when {
        succeeded > 0 -> Status.SUCCESS
        failed == children.size -> Status.FAILURE
        else -> Status.RUNNING
      }
    }
  }
}
