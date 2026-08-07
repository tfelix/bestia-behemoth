package net.bestia.zone.ai.bt

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.ImmediateSuccess
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for the behaviour-tree library itself — composites, decorators and the DSL — with no ECS
 * behaviour involved. The leaves here are stubs that just report a canned status, so a failure points
 * at the tree wiring rather than at anything a mob does.
 */
class BehaviourTreeTest {

  /** A leaf that reports [status] and counts how often it was ticked. */
  private class Stub(private val status: Status) : BtNode {
    var ticks = 0
      private set

    override fun tick(context: BtContext): Status {
      ticks++
      return status
    }
  }

  /** A leaf that fails [failFor] ticks and then succeeds forever. */
  private class SucceedsAfter(private val failFor: Int) : BtNode {
    private var seen = 0
    override fun tick(context: BtContext): Status {
      seen++
      return if (seen > failFor) Status.SUCCESS else Status.FAILURE
    }
  }

  private fun context(deltaTime: Float = 0.05f): BtContext {
    val world = testWorld()
    val id = world.createEntity { }
    return BtContext(
      world = world,
      entityId = id,
      memory = Blackboard(),
      state = WorldState.EMPTY,
      deltaTime = deltaTime,
      currentTick = 0L,
      tickRate = 20,
    )
  }

  // ----------------------------------------------------------------- composites

  @Test
  fun `sequence succeeds only when every child succeeds`() {
    val a = Stub(Status.SUCCESS)
    val b = Stub(Status.SUCCESS)

    assertEquals(Status.SUCCESS, SequenceNode(a, b).tick(context()))
    assertEquals(1, a.ticks)
    assertEquals(1, b.ticks)
  }

  @Test
  fun `sequence stops at the first failure and does not tick later children`() {
    val first = Stub(Status.FAILURE)
    val later = Stub(Status.SUCCESS)

    assertEquals(Status.FAILURE, SequenceNode(first, later).tick(context()))
    assertEquals(0, later.ticks, "a child after a failed one must not run")
  }

  @Test
  fun `sequence suspends on a running child`() {
    val running = Stub(Status.RUNNING)
    val later = Stub(Status.SUCCESS)

    assertEquals(Status.RUNNING, SequenceNode(running, later).tick(context()))
    assertEquals(0, later.ticks)
  }

  @Test
  fun `sequence re-evaluates earlier children every tick rather than resuming`() {
    val guard = Stub(Status.SUCCESS)
    val body = Stub(Status.RUNNING)
    val tree = SequenceNode(guard, body)
    val ctx = context()

    tree.tick(ctx)
    tree.tick(ctx)
    tree.tick(ctx)

    // The reactive contract: the guard is re-checked on every tick, so a condition going false
    // aborts a running body instead of being skipped over.
    assertEquals(3, guard.ticks)
    assertEquals(3, body.ticks)
  }

  @Test
  fun `selector takes the first child that does not fail`() {
    val declined = Stub(Status.FAILURE)
    val chosen = Stub(Status.SUCCESS)
    val never = Stub(Status.SUCCESS)

    assertEquals(Status.SUCCESS, SelectorNode(declined, chosen, never).tick(context()))
    assertEquals(1, declined.ticks)
    assertEquals(1, chosen.ticks)
    assertEquals(0, never.ticks, "a child after the chosen one must not run")
  }

  @Test
  fun `selector fails only when every child fails`() {
    assertEquals(
      Status.FAILURE,
      SelectorNode(Stub(Status.FAILURE), Stub(Status.FAILURE)).tick(context()),
    )
  }

  @Test
  fun `parallel requiring all ticks every child and fails if any fails`() {
    val ok = Stub(Status.SUCCESS)
    val bad = Stub(Status.FAILURE)
    val running = Stub(Status.RUNNING)

    val status = ParallelNode(listOf(ok, bad, running), ParallelPolicy.REQUIRE_ALL).tick(context())

    assertEquals(Status.FAILURE, status)
    // The point of parallel: no short-circuit, every child got its tick.
    assertEquals(1, ok.ticks)
    assertEquals(1, bad.ticks)
    assertEquals(1, running.ticks)
  }

  @Test
  fun `parallel requiring all is running until all have succeeded`() {
    val nodes = listOf(Stub(Status.SUCCESS), Stub(Status.RUNNING))
    assertEquals(Status.RUNNING, ParallelNode(nodes, ParallelPolicy.REQUIRE_ALL).tick(context()))

    val allDone = listOf(Stub(Status.SUCCESS), Stub(Status.SUCCESS))
    assertEquals(Status.SUCCESS, ParallelNode(allDone, ParallelPolicy.REQUIRE_ALL).tick(context()))
  }

  @Test
  fun `parallel requiring one succeeds on a single success and fails only when all fail`() {
    val mixed = listOf(Stub(Status.FAILURE), Stub(Status.SUCCESS))
    assertEquals(Status.SUCCESS, ParallelNode(mixed, ParallelPolicy.REQUIRE_ONE).tick(context()))

    val allFailed = listOf(Stub(Status.FAILURE), Stub(Status.FAILURE))
    assertEquals(Status.FAILURE, ParallelNode(allFailed, ParallelPolicy.REQUIRE_ONE).tick(context()))
  }

  // ----------------------------------------------------------------- decorators

  @Test
  fun `inverter swaps success and failure but passes running through`() {
    val ctx = context()
    assertEquals(Status.FAILURE, Inverter(Stub(Status.SUCCESS)).tick(ctx))
    assertEquals(Status.SUCCESS, Inverter(Stub(Status.FAILURE)).tick(ctx))
    assertEquals(Status.RUNNING, Inverter(Stub(Status.RUNNING)).tick(ctx))
  }

  @Test
  fun `succeeder turns failure into success`() {
    val ctx = context()
    assertEquals(Status.SUCCESS, Succeeder(Stub(Status.FAILURE)).tick(ctx))
    assertEquals(Status.RUNNING, Succeeder(Stub(Status.RUNNING)).tick(ctx))
  }

  @Test
  fun `repeat succeeds once the child has succeeded the requested number of times`() {
    val child = Stub(Status.SUCCESS)
    val tree = Repeat(child, times = 3)
    val ctx = context()

    assertEquals(Status.RUNNING, tree.tick(ctx))
    assertEquals(Status.RUNNING, tree.tick(ctx))
    assertEquals(Status.SUCCESS, tree.tick(ctx))
    assertEquals(3, child.ticks)
  }

  @Test
  fun `repeat fails immediately when the child fails rather than spinning`() {
    assertEquals(Status.FAILURE, Repeat(Stub(Status.FAILURE), times = 3).tick(context()))
  }

  @Test
  fun `cooldown allows the first success then blocks until the interval has elapsed`() {
    val child = Stub(Status.SUCCESS)
    // 1 second cooldown, ticked at 0.5s a time.
    val tree = Cooldown(child, seconds = 1.0f)
    val ctx = context(deltaTime = 0.5f)

    assertEquals(Status.SUCCESS, tree.tick(ctx), "a fresh tree is not on cooldown")
    assertEquals(Status.FAILURE, tree.tick(ctx), "0.5s elapsed of a 1s cooldown")
    assertEquals(Status.FAILURE, tree.tick(ctx), "1.0s elapsed, expires on this tick")
    assertEquals(Status.SUCCESS, tree.tick(ctx), "cooldown spent, the child runs again")

    assertEquals(2, child.ticks, "the child must not be ticked while cooling down")
  }

  @Test
  fun `cooldown does not start until the child actually succeeds`() {
    val child = SucceedsAfter(failFor = 2)
    val tree = Cooldown(child, seconds = 1.0f)
    val ctx = context(deltaTime = 0.5f)

    // A child that is still failing must not consume the cooldown; otherwise a rate-limited action
    // that never got to act would be punished for it.
    assertEquals(Status.FAILURE, tree.tick(ctx))
    assertEquals(Status.FAILURE, tree.tick(ctx))
    assertEquals(Status.SUCCESS, tree.tick(ctx))
    assertEquals(Status.FAILURE, tree.tick(ctx), "now on cooldown")
  }

  // ------------------------------------------------------------------------ DSL

  @Test
  fun `dsl builds the tree in written order`() {
    val order = mutableListOf<String>()
    val tree = sequence {
      condition("first") { order += "first"; true }
      run("second") { order += "second"; Status.SUCCESS }
    }

    assertEquals(Status.SUCCESS, tree.tick(context()))
    assertEquals(listOf("first", "second"), order)
  }

  @Test
  fun `dsl condition failing short-circuits the sequence`() {
    var ran = false
    val tree = sequence {
      condition("never") { false }
      run("body") { ran = true; Status.SUCCESS }
    }

    assertEquals(Status.FAILURE, tree.tick(context()))
    assertEquals(false, ran)
  }

  @Test
  fun `dsl invert negates a condition`() {
    val tree = sequence {
      invert { condition("hostile in sight") { false } }
      run("relax") { Status.SUCCESS }
    }

    assertEquals(Status.SUCCESS, tree.tick(context()))
  }

  @Test
  fun `dsl optional keeps a failing step from aborting the sequence`() {
    val tree = sequence {
      optional { run("flourish") { Status.FAILURE } }
      run("strike") { Status.SUCCESS }
    }

    assertEquals(Status.SUCCESS, tree.tick(context()))
  }

  @Test
  fun `dsl decorator block with several statements folds into a sequence`() {
    val tree = invertedPair()
    // Both children succeed, so the folded sequence succeeds and the inverter turns it into FAILURE.
    assertEquals(Status.FAILURE, tree.tick(context()))
  }

  private fun invertedPair(): BtNode = sequence {
    invert {
      condition("a") { true }
      condition("b") { true }
    }
  }

  @Test
  fun `dsl can attach a hand-written node`() {
    val tree = sequence { node(ImmediateSuccess) }
    assertEquals(Status.SUCCESS, tree.tick(context()))
  }
}
