package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.CommonKeys
import net.bestia.zone.ai.core.state.RestingWindow
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.entity.Animation
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * That when an agent is off duty is the agent's own answer.
 *
 * Perception clears the rested latch and runs over everything in the world, so it used to read the
 * *profile's* activity cycle - which defaults to cathemeral and knows only about the sun. A night watchman
 * would therefore have the latch cleared all night, leaving sleep permanently unsatisfied, and since sleep
 * outranks working he would go home instead of standing his post.
 */
class RestingWindowTest {

  private lateinit var ai: AiPipelineFixture

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  @Test
  fun `a diurnal creature has its latch cleared at night`() {
    ai.setNight()
    val mob = ai.spawnMob(DIURNAL, Vec3L(0, 0, 0))
    ai.agentOf(mob).memory.set(CommonKeys.RESTED, true)

    ai.tick(TICKS_PER_SWEEP)

    assertNull(
      ai.beliefOf(mob, CommonKeys.RESTED),
      "a diurnal animal's night is its resting phase, so the latch has to come off and let it go to bed"
    )
  }

  @Test
  fun `a diurnal creature keeps its latch through the day`() {
    ai.setDay()
    val mob = ai.spawnMob(DIURNAL, Vec3L(0, 0, 0))
    ai.agentOf(mob).memory.set(CommonKeys.RESTED, true)

    ai.tick(TICKS_PER_SWEEP)

    assertEquals(true, ai.beliefOf(mob, CommonKeys.RESTED), "noon is not a grazer's bedtime")
  }

  @Test
  fun `a night watchman keeps its latch through the small hours`() {
    ai.now = ai.now.copy(hour = 2)
    val guard = spawnWithWindow(nightShift())
    ai.agentOf(guard).memory.set(CommonKeys.RESTED, true)

    ai.tick(TICKS_PER_SWEEP)

    assertEquals(
      true,
      ai.beliefOf(guard, CommonKeys.RESTED),
      "02:00 is the middle of a night watch, not the middle of its sleep - clearing the latch here is " +
        "what sends the guard home in the dark"
    )
  }

  @Test
  fun `a night watchman has its latch cleared once it is off duty`() {
    ai.now = ai.now.copy(hour = 10)
    val guard = spawnWithWindow(nightShift())
    ai.agentOf(guard).memory.set(CommonKeys.RESTED, true)

    ai.tick(TICKS_PER_SWEEP)

    assertNull(ai.beliefOf(guard, CommonKeys.RESTED), "a watchman off duty at ten in the morning sleeps")
  }

  @Test
  fun `perception publishes the minute and the day`() {
    ai.now = ai.now.copy(hour = 7)
    val mob = ai.spawnMob(DIURNAL, Vec3L(0, 0, 0))

    ai.tick(TICKS_PER_SWEEP)

    assertEquals(7 * 60, ai.beliefOf(mob, CommonKeys.MINUTE_OF_DAY))
    assertEquals(ai.now.absoluteDay.toLong(), ai.beliefOf(mob, CommonKeys.DAY_INDEX))
  }

  @Test
  fun `the day index moves when the calendar rolls over`() {
    val mob = ai.spawnMob(DIURNAL, Vec3L(0, 0, 0))
    ai.tick(TICKS_PER_SWEEP)
    val today = ai.beliefOf(mob, CommonKeys.DAY_INDEX)!!

    ai.advanceHours(24)
    ai.tick(TICKS_PER_SWEEP)

    assertEquals(today + 1, ai.beliefOf(mob, CommonKeys.DAY_INDEX), "a day passed and nothing noticed")
  }

  /** Awake 18:00 to 06:00, so the resting window is the daylight either side of it. */
  private fun nightShift(): RestingWindow {
    return RestingWindow { minuteOfDay, _ -> minuteOfDay in 6 * 60 until 18 * 60 }
  }

  private fun spawnWithWindow(window: RestingWindow): EntityId {
    val profile = ai.profiles.getOrThrow(DIURNAL)
    val built = ai.agentFactory.create(profile, homePosition = Vec3L(0, 0, 0))

    return ai.world.createEntity { id ->
      ai.world.add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      ai.world.add(id, Health(10, 10))
      ai.world.add(id, Speed())
      ai.world.add(id, Animation())
      ai.world.add(
        id,
        AiAgent(
          profileId = built.profileId,
          name = built.name,
          goals = built.goals,
          actionResolver = built.actionResolver,
          memory = built.memory,
          teamMemory = built.teamMemory,
          drives = built.drives,
          restingWindow = window
        )
      )
    }
  }

  private companion object {
    const val DIURNAL = "passiv_day_active"

    /** Perception sweeps twice a second and the world ticks twenty times, so this is one full sweep. */
    const val TICKS_PER_SWEEP = 12
  }
}
