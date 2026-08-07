package net.bestia.zone.ai.bt

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status

/**
 * A leaf that only answers a question. SUCCESS when [predicate] holds, FAILURE otherwise, never
 * RUNNING — checking something takes no time.
 *
 * [description] exists so a tree is legible in a debugger and a log line, which a bare lambda is not.
 */
class ConditionLeaf(
  private val description: String,
  private val predicate: (BtContext) -> Boolean,
) : BtNode {
  override fun tick(context: BtContext): Status =
    if (predicate(context)) Status.SUCCESS else Status.FAILURE

  override fun toString(): String = "condition($description)"
}

/** A leaf that does something and reports its own [Status]. See [ConditionLeaf] for [description]. */
class ActionLeaf(
  private val description: String,
  private val body: (BtContext) -> Status,
) : BtNode {
  override fun tick(context: BtContext): Status = body(context)
  override fun toString(): String = "run($description)"
}

/**
 * Collects the children of one composite. Every method appends exactly one node, so the order they
 * are written in is the order they are ticked in — which is the whole meaning of a sequence or a
 * selector.
 *
 * Marked [BtDslMarker] so a nested block cannot accidentally reach the outer builder and attach a
 * child to the wrong parent.
 */
@BtDslMarker
class BtBuilder {

  private val children = mutableListOf<BtNode>()

  /** Attach an already-built node, e.g. a hand-written leaf class like `MoveTo`. */
  fun node(node: BtNode) {
    children += node
  }

  operator fun BtNode.unaryPlus() = node(this)

  fun sequence(block: BtBuilder.() -> Unit) = node(net.bestia.zone.ai.bt.sequence(block))

  fun selector(block: BtBuilder.() -> Unit) = node(net.bestia.zone.ai.bt.selector(block))

  fun parallel(policy: ParallelPolicy = ParallelPolicy.REQUIRE_ALL, block: BtBuilder.() -> Unit) =
    node(net.bestia.zone.ai.bt.parallel(policy, block))

  /** SUCCESS when [predicate] holds. */
  fun condition(description: String, predicate: (BtContext) -> Boolean) =
    node(ConditionLeaf(description, predicate))

  /** Does something over one or more ticks and reports its own status. */
  fun run(description: String, body: (BtContext) -> Status) =
    node(ActionLeaf(description, body))

  /** Negates whatever the block builds. Wraps a [sequence] if the block has several children. */
  fun invert(block: BtBuilder.() -> Unit) = node(Inverter(single(block)))

  /** Runs the block but never fails, for a genuinely optional step. */
  fun optional(block: BtBuilder.() -> Unit) = node(Succeeder(single(block)))

  /** Repeats the block until it has succeeded [times] times. */
  fun repeat(times: Int, block: BtBuilder.() -> Unit) = node(Repeat(single(block), times))

  /** Rate-limits the block to at most one success every [seconds]; FAILURE while cooling down. */
  fun cooldown(seconds: Float, block: BtBuilder.() -> Unit) = node(Cooldown(single(block), seconds))

  internal fun build(): List<BtNode> = children.toList()

  /**
   * A decorator wraps exactly one child, but writing several statements in its block is natural, so a
   * multi-statement block is folded into a sequence rather than rejected.
   */
  private fun single(block: BtBuilder.() -> Unit): BtNode {
    val built = BtBuilder().apply(block).build()
    require(built.isNotEmpty()) { "decorator block must build at least one node" }
    return built.singleOrNull() ?: SequenceNode(built)
  }
}

@DslMarker
annotation class BtDslMarker

/**
 * Builds a reactive sequence — every child must succeed, in order.
 *
 * ```
 * behavior = {
 *   sequence {
 *     condition("target still alive") { it.world.isAlive(targetId) }
 *     cooldown(1.5f) { run("strike") { ctx -> strike(ctx, targetId) } }
 *   }
 * }
 * ```
 */
fun sequence(block: BtBuilder.() -> Unit): BtNode = SequenceNode(BtBuilder().apply(block).build())

/** Builds a reactive selector — the first child that does not fail wins. */
fun selector(block: BtBuilder.() -> Unit): BtNode = SelectorNode(BtBuilder().apply(block).build())

/** Builds a [ParallelNode] — every child ticked every tick, aggregated per [policy]. */
fun parallel(policy: ParallelPolicy = ParallelPolicy.REQUIRE_ALL, block: BtBuilder.() -> Unit): BtNode =
  ParallelNode(BtBuilder().apply(block).build(), policy)
