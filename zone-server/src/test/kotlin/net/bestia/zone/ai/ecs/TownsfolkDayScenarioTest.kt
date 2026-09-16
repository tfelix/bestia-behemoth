package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ecs.entity.Animation
import net.bestia.zone.ecs.spawn.townsfolk.Townsfolk
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A townsperson's whole day, through the real pipeline: perception, drives, planner, behaviour, movement.
 *
 * [net.bestia.zone.ai.domain.townsfolk.TownsfolkDayTest] settles what the planner *decides*; this settles
 * that the decisions survive contact with the world - that the hour reaches memory at all, that the walk
 * home actually moves the entity, and that morning ends the sleep rather than the sleep ending itself.
 */
class TownsfolkDayScenarioTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(100, 100, 0)

  /** Out of loitering range, as a real barracks would be - see `TownsfolkDayTest`. */
  private val post = Vec3L(100 + TownsfolkDomain.DEFAULT_LOITER_RADIUS + 20, 100, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  @Test
  fun `by day a townsperson is out and about`() {
    val commoner = ai.spawnMob("townsfolk_commoner", home)

    ai.tickUntilGoal(commoner, "Loiter")
  }

  @Test
  fun `at bedtime it walks home and lies down`() {
    val commoner = ai.spawnMob("townsfolk_commoner", home)

    // Long enough to have loitered somewhere other than the doorstep, so going home is a real journey.
    ai.tickUntilGoal(commoner, "Loiter")
    ai.tickUntil(describe = { "it never left the doorstep, so there would be nothing to walk back" }) {
      ai.positionOf(commoner).distance(home) > TownsfolkDomain.DOORSTEP_RADIUS
    }

    ai.advanceTo(TownsfolkDomain.BEDTIME_HOUR)

    ai.tickUntil(
      maxTicks = 20 * 120,
      describe = { "it never got to bed (goal=${ai.goalNameOf(commoner)}, at=${ai.positionOf(commoner)})" }
    ) {
      ai.animationOf(commoner) == Animation.AnimationKind.SLEEP
    }

    assertEquals(
      "Sleep",
      ai.goalNameOf(commoner),
    )
    assertTrue(
      ai.positionOf(commoner).distance(home) <= TownsfolkDomain.DOORSTEP_RADIUS,
      "it fell asleep ${ai.positionOf(commoner).distance(home)} tiles from home rather than in its own bed"
    )
  }

  @Test
  fun `morning gets it up again`() {
    val commoner = ai.spawnMob("townsfolk_commoner", home)
    ai.advanceTo(TownsfolkDomain.BEDTIME_HOUR)
    ai.tickUntil(maxTicks = 20 * 120, describe = { "it never got to bed" }) {
      ai.animationOf(commoner) == Animation.AnimationKind.SLEEP
    }

    ai.advanceTo(TownsfolkDomain.RISE_HOUR + 1)

    ai.tickUntil(describe = { "it slept through the morning (goal=${ai.goalNameOf(commoner)})" }) {
      ai.goalNameOf(commoner) != "Sleep"
    }
    assertNotEquals(Animation.AnimationKind.SLEEP, ai.animationOf(commoner), "posture must not stay latched")
  }

  @Test
  fun `a guard mans his post through the shift and leaves when it ends`() {
    val guard = ai.spawnTownsfolk(GUARD, home = home, post = post)

    ai.advanceTo(9)
    ai.tickUntil(
      maxTicks = 20 * 180,
      describe = { "the guard never reached his post (goal=${ai.goalNameOf(guard)}, at=${ai.positionOf(guard)})" }
    ) {
      ai.goalNameOf(guard) == "WorkShift" &&
        ai.positionOf(guard).distance(post) <= TownsfolkDomain.DOORSTEP_RADIUS
    }

    // Standing a post is standing still. A leftover path from the walk over would have him drift off it,
    // which is what `StandStill` inside the shift exists to prevent.
    ai.tick(times = 20 * 10)
    assertTrue(
      ai.positionOf(guard).distance(post) <= TownsfolkDomain.DOORSTEP_RADIUS,
      "he wandered ${ai.positionOf(guard).distance(post)} tiles off his post while on duty"
    )

    ai.advanceTo(GUARD.shift!!.toHour)

    ai.tickUntil(describe = { "the shift never ended (goal=${ai.goalNameOf(guard)})" }) {
      ai.goalNameOf(guard) != "WorkShift"
    }
  }

  @Test
  fun `tomorrow he turns up again, with nobody having reset him`() {
    val guard = ai.spawnTownsfolk(GUARD, home = home, post = post)
    ai.advanceTo(9)
    ai.tickUntil(maxTicks = 20 * 180, describe = { "the guard never started work" }) {
      ai.goalNameOf(guard) == "WorkShift"
    }

    ai.advanceTo(GUARD.shift!!.toHour)
    ai.tickUntil(describe = { "the shift never ended" }) { ai.goalNameOf(guard) != "WorkShift" }
    val yesterday = ai.agentOf(guard).memory.get(TownsfolkDomain.DAY_INDEX)

    // Round to the same hour the next morning. If a day's work left anything latched behind it, this is
    // where it shows: he would keep to the street instead of turning up.
    ai.advanceTo(9)

    ai.tickUntil(
      maxTicks = 20 * 180,
      describe = { "he never went back on duty (goal=${ai.goalNameOf(guard)})" }
    ) {
      ai.goalNameOf(guard) == "WorkShift"
    }
    assertNotEquals(
      yesterday,
      ai.agentOf(guard).memory.get(TownsfolkDomain.DAY_INDEX),
      "the calendar never moved on, so this proves nothing"
    )
  }

  @Test
  fun `somebody with a house goes into it, and stops existing`() {
    val villager = ai.spawnTownsfolk(GUARD, home = home, post = post, homeBuilding = HOUSE)
    val identity = ai.world.getOrThrow(villager, Townsfolk::class).identity

    ai.advanceTo(TownsfolkDomain.BEDTIME_HOUR)

    ai.tickUntil(
      maxTicks = 20 * 180,
      describe = { "the villager never went inside (goal=${ai.goalNameOf(villager)})" }
    ) {
      ai.indoors.isIndoors(identity)
    }

    // The whole point of a door: a town asleep costs a map entry per person, not an entity apiece.
    ai.tick(times = 2)
    assertFalse(ai.world.hasEntity(villager), "the entity is still standing about after going indoors")
  }

  @Test
  fun `they went in by their own front door, and will come out of it`() {
    val villager = ai.spawnTownsfolk(GUARD, home = home, post = post, homeBuilding = HOUSE)
    val identity = ai.world.getOrThrow(villager, Townsfolk::class).identity

    ai.advanceTo(TownsfolkDomain.BEDTIME_HOUR)
    ai.tickUntil(maxTicks = 20 * 180, describe = { "the villager never went inside" }) {
      ai.indoors.isIndoors(identity)
    }

    assertEquals(home, ai.indoors.leave(identity)?.door)
  }

  @Test
  fun `the hour of day reaches its memory`() {
    // The one thing every timetable from here on rests on. IS_NIGHT cannot carry it: full night is 22:00 to
    // 04:00 while a commoner rises at 06:00, and an occupation's shift will want finer still.
    val commoner = ai.spawnMob("townsfolk_commoner", home)
    ai.advanceTo(9)

    ai.tickUntil(describe = { "perception never wrote the clock" }) {
      ai.agentOf(commoner).memory.get(TownsfolkDomain.MINUTE_OF_DAY) != null
    }

    assertEquals(9 * 60, ai.agentOf(commoner).memory.get(TownsfolkDomain.MINUTE_OF_DAY))
  }

  private companion object {
    val GUARD = Occupation("guard", "guard", "barracks", HourWindow(6, 18), HourWindow(22, 6))

    /** Any prop id will do; what matters to the plan is that there is one. */
    const val HOUSE = 9_001L
  }
}
