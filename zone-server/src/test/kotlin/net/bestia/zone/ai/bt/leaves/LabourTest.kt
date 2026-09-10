package net.bestia.zone.ai.bt.leaves

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Work that banks what it finishes.
 *
 * The point of the leaf is that a shift is not all-or-nothing. Anything a townsperson does is liable to be
 * cut short - hunger interrupts them twice a day and a brawl outside interrupts everybody - so work that
 * only counted at the end of the shift would almost never count.
 */
class LabourTest {

  private val world = testWorld()
  private val entityId: EntityId = world.createEntity { }

  private var supplied = true
  private var finished = 0

  private val sut = Labour(
    secondsPerUnit = SECONDS,
    canBegin = { supplied },
    onFinished = { finished++ },
  )

  @Test
  fun `a piece of work takes as long as it takes`() {
    tick(SECONDS * 0.9f)

    assertEquals(0, finished, "the work was done before the time was up")
    assertEquals(Status.RUNNING, tick(0f))
  }

  @Test
  fun `and is banked the moment it is done`() {
    tick(SECONDS)

    assertEquals(1, finished)
  }

  @Test
  fun `one after another, for as long as the shift lasts`() {
    repeat(3) { tick(SECONDS) }

    assertEquals(3, finished)
  }

  @Test
  fun `an odd tick length does not lose the remainder`() {
    // Frames do not divide into work. Resetting the clock rather than carrying it would throw away most of
    // a unit every time, and the loss would be invisible - just a baker who is quietly slower than the one
    // in the next town.
    repeat(30) { tick(SECONDS * 0.4f) }

    assertEquals(12, finished, "twelve units of work took ${30 * 0.4f} units of time")
  }

  @Test
  fun `finding the store empty ends the shift rather than pausing it`() {
    supplied = false

    assertEquals(Status.FAILURE, tick(0f))
  }

  @Test
  fun `running out halfway keeps what was already made`() {
    tick(SECONDS)
    supplied = false

    assertEquals(Status.FAILURE, tick(SECONDS))
    assertEquals(1, finished, "the loaf already baked was thrown away with the flour that ran out")
  }

  @Test
  fun `the walk that brought them here is dropped`() {
    // StandStill's guard, for StandStill's reason: waypoints already handed to the movement system outlive
    // the plan that asked for them, so a worker would stroll away from the bench.
    world.add(entityId, Path(mutableListOf(Vec3L(50, 50, 0))))

    tick(0f)

    assertFalse(world.has(entityId, Path::class), "the worker kept walking after settling down to work")
  }

  private fun tick(deltaTime: Float): Status {
    return sut.tick(
      BtContext(
        world = world,
        entityId = entityId,
        memory = Blackboard(),
        state = WorldState.EMPTY,
        deltaTime = deltaTime,
        currentTick = 0,
        tickRate = 20,
      )
    )
  }

  private companion object {
    const val SECONDS = 10f
  }
}
