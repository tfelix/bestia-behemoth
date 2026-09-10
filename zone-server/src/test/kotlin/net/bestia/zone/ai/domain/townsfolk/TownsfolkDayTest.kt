package net.bestia.zone.ai.domain.townsfolk

import net.bestia.zone.ai.core.agent.SimpleAgent
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * What a townsperson decides at each hour of a day with nothing in it but a bedtime.
 *
 * The first two cases are what the domain is shaped around. Sleeping is gated on standing at one's own
 * door, so bedtime is a *journey* the planner works out rather than a place the NPC happens to be - and
 * the away/at-home pair is what shows the gate produces that journey instead of blocking sleep outright.
 */
class TownsfolkDayTest {

  private val planner = Planner()
  private val home = Vec3L(100, 100, 0)

  @Test
  fun `at bedtime, out in the street, the plan is to walk home and lie down`() {
    val plan = planner.makePlanForAgent(townsperson(at = Vec3L(108, 100, 0), hour = 22), Blackboard())

    assertEquals("Sleep", plan?.goal?.name)
    assertEquals(listOf("goHome", "sleepAtHome"), plan?.actions?.map { it.name })
  }

  @Test
  fun `at bedtime, already at the door, there is nothing to do but sleep`() {
    val plan = planner.makePlanForAgent(townsperson(at = home, hour = 22), Blackboard())

    assertEquals(listOf("sleepAtHome"), plan?.actions?.map { it.name })
  }

  @Test
  fun `come morning, bed is no longer on the agenda`() {
    val agent = townsperson(at = home, hour = TownsfolkDomain.RISE_HOUR, tiredness = 5, rested = true)
    val sleep = TownsfolkDomain.Goals.BY_NAME.getValue("Sleep")

    assertFalse(
      sleep.isAvailable(agent.memory.snapshot()),
      "sleep is still available at ${TownsfolkDomain.RISE_HOUR}:00, so nobody would ever get up"
    )
  }

  @Test
  fun `by day a restless townsperson loiters`() {
    val plan = planner.makePlanForAgent(townsperson(at = home, hour = NOON), Blackboard())

    assertEquals("Loiter", plan?.goal?.name)
    assertEquals(listOf("loiter"), plan?.actions?.map { it.name })
  }

  @Test
  fun `having drifted, going home outranks ambling further`() {
    val strayed = Vec3L(100 + TownsfolkDomain.DEFAULT_LOITER_RADIUS + 5, 100, 0)

    assertEquals("GoHome", planner.makePlanForAgent(townsperson(at = strayed, hour = NOON), Blackboard())?.goal?.name)
  }

  @Test
  fun `there is always something to do`() {
    // The floor goal, stated as the property it exists for. Nothing here is pressing - broad daylight or
    // dead of night, well rested, standing at home - and an hour that came up empty would leave the think
    // stage with no plan and the townsperson rooted to the spot until something else changed.
    for (hour in 0 until 24) {
      val agent = townsperson(at = home, hour = hour, tiredness = 0, rested = true)

      assertNotNull(
        planner.makePlanForAgent(agent, Blackboard()),
        "a townsperson at $hour:00 has no plan at all, so it would stand still indefinitely"
      )
    }
  }

  private fun townsperson(
    at: Vec3L,
    hour: Int,
    tiredness: Int = 30,
    restlessness: Int = 100,
    rested: Boolean = false,
  ): SimpleAgent {
    val memory = Blackboard().apply {
      set(TownsfolkDomain.POSITION, at, Blackboard.PERMANENT)
      set(TownsfolkDomain.HOME_POSITION, home, Blackboard.PERMANENT)
      set(TownsfolkDomain.HOUR_OF_DAY, hour, Blackboard.PERMANENT)
      set(TownsfolkDomain.WANDER_RADIUS, TownsfolkDomain.DEFAULT_LOITER_RADIUS, Blackboard.PERMANENT)
      set(TownsfolkDomain.TIREDNESS, tiredness, Blackboard.PERMANENT)
      set(TownsfolkDomain.RESTLESSNESS, restlessness, Blackboard.PERMANENT)
      if (rested) set(TownsfolkDomain.RESTED, true, Blackboard.PERMANENT)
    }

    return SimpleAgent(
      name = "commoner",
      goals = TownsfolkDomain.Goals.ALL,
      memory = memory,
      actionResolver = TownsfolkDomainFixture.resolver(),
    )
  }

  private companion object {
    const val NOON = 12
  }
}
