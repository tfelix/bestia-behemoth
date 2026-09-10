package net.bestia.zone.ai.bt

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.CommonKeys
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ecs.core.testWorld
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That a span of the working day is measured against the clock rather than counted down.
 *
 * The last test is the one this decorator exists for. A plan step's tree is rebuilt on every adoption, so
 * anything holding a countdown restarts whenever the agent reconsiders - and an agent reconsiders often.
 * A guard on a twelve-hour timer who glanced at a passer-by at five in the afternoon would then be on duty
 * until five the following morning, which is a bug that only appears after somebody watches a whole day.
 */
class UntilHourTest {

  private val shift = HourWindow(6, 18)
  private val memory = Blackboard()

  private var childTicks = 0

  private val child = object : BtNode {
    override fun tick(context: BtContext): Status {
      childTicks++
      return Status.SUCCESS
    }
  }

  @Test
  fun `it runs while the clock is inside the span`() {
    memory.set(CommonKeys.HOUR_OF_DAY, 9, Blackboard.PERMANENT)

    assertEquals(Status.RUNNING, UntilHour(shift, child).tick(context()))
  }

  @Test
  fun `the child finishing does not finish the span`() {
    // A shift is a length of time and the work inside it repeats, so a child that succeeds is asked again.
    memory.set(CommonKeys.HOUR_OF_DAY, 9, Blackboard.PERMANENT)
    val sut = UntilHour(shift, child)
    val context = context()

    repeat(3) { assertEquals(Status.RUNNING, sut.tick(context)) }
    assertEquals(3, childTicks, "the child should have been asked once per tick")
  }

  @Test
  fun `it succeeds once the clock leaves the span`() {
    memory.set(CommonKeys.HOUR_OF_DAY, 18, Blackboard.PERMANENT)

    assertEquals(Status.SUCCESS, UntilHour(shift, child).tick(context()))
    assertEquals(0, childTicks, "the shift was over before it started, so there was nothing to do")
  }

  @Test
  fun `an unknown hour fails rather than waiting forever`() {
    assertEquals(Status.FAILURE, UntilHour(shift, child).tick(context()))
  }

  @Test
  fun `a fresh tree does not restart the span`() {
    // Two separate adoptions of the same action, the second late in the shift. A countdown would give the
    // second one the full twelve hours; the hour gives it the twenty minutes actually left.
    memory.set(CommonKeys.HOUR_OF_DAY, 9, Blackboard.PERMANENT)
    assertEquals(Status.RUNNING, UntilHour(shift, child).tick(context()))

    memory.set(CommonKeys.HOUR_OF_DAY, 18, Blackboard.PERMANENT)
    assertEquals(Status.SUCCESS, UntilHour(shift, child).tick(context()), "a rebuilt tree still knows the time")
  }

  private fun context(): BtContext {
    val world = testWorld()
    val id = world.createEntity { }

    return BtContext(
      world = world,
      entityId = id,
      memory = memory,
      state = WorldState.EMPTY,
      deltaTime = 0.05f,
      currentTick = 0L,
      tickRate = 20,
    )
  }
}
