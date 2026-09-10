package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ecs.entity.Animation
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
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
  fun `the hour of day reaches its memory`() {
    // The one thing every timetable from here on rests on. IS_NIGHT cannot carry it: full night is 22:00 to
    // 04:00 while a commoner rises at 06:00, and an occupation's shift will want finer still.
    val commoner = ai.spawnMob("townsfolk_commoner", home)
    ai.advanceTo(9)

    ai.tickUntil(describe = { "perception never wrote the hour" }) {
      ai.agentOf(commoner).memory.get(TownsfolkDomain.HOUR_OF_DAY) != null
    }

    assertEquals(9, ai.agentOf(commoner).memory.get(TownsfolkDomain.HOUR_OF_DAY))
  }
}
