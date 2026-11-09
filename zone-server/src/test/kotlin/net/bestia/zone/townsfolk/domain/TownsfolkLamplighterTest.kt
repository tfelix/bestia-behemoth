package net.bestia.zone.townsfolk.domain

import net.bestia.zone.ai.core.agent.SimpleAgent
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The lamplighter goes down the lantern line at dusk on most evenings, and nobody else ever does.
 *
 * The round is planned like every other errand: a goal available at the hour, an action that walks the route,
 * a day stamp so it is walked once. What is asserted is that the planner chains it, that it wins against the
 * evening's other pulls, and that it stays out of everybody else's day.
 */
class TownsfolkLamplighterTest {

  private val planner = Planner()
  private val home = Vec3L(100, 100, 0)
  private val route = listOf(Vec3L(140, 100, 0), Vec3L(190, 100, 0), Vec3L(240, 102, 0))
  private val lamplighter = Occupation("lamplighter", "lamplighter", null, null, HourWindow(23, 7))
  private val labourer = Occupation("labourer", "labourer", null, HourWindow(7, 17), HourWindow(22, 6))

  @Test
  fun `at dusk on a tending evening the lamplighter walks the line`() {
    val day = (0L..200L).first { TownsfolkDomain.tendsTonight(state(it)) }
    val plan = planner.makePlanForAgent(agent(lamplighter, hour = 20, day = day), Blackboard())

    assertEquals("TendLanterns", plan?.goal?.name)
    assertEquals(listOf("tendLanterns"), plan?.actions?.map { it.name })
  }

  @Test
  fun `some evenings the round is not walked at all`() {
    val days = (0L..200L)
    val walked = days.count { TownsfolkDomain.tendsTonight(state(it)) }
    assertTrue(walked in 80..160, "walked $walked of ${days.count()} evenings; expected about three in five")
  }

  @Test
  fun `by day, and once walked, the round is off the list`() {
    val day = (0L..200L).first { TownsfolkDomain.tendsTonight(state(it)) }
    val noon = planner.makePlanForAgent(agent(lamplighter, hour = 12, day = day), Blackboard())
    assertNotEquals("TendLanterns", noon?.goal?.name)

    val done = planner.makePlanForAgent(agent(lamplighter, hour = 20, day = day, tendedOn = day), Blackboard())
    assertNotEquals("TendLanterns", done?.goal?.name)
  }

  @Test
  fun `nobody without a route tends lanterns`() {
    val day = (0L..200L).first { TownsfolkDomain.tendsTonight(state(it)) }
    val plan = planner.makePlanForAgent(agent(labourer, hour = 20, day = day, withRoute = false), Blackboard())
    assertNotEquals("TendLanterns", plan?.goal?.name)
  }

  private fun state(day: Long) = Blackboard().apply {
    set(TownsfolkDomain.DAY_INDEX, day, Blackboard.PERMANENT)
    set(TownsfolkDomain.HOME_BUILDING, HOUSE, Blackboard.PERMANENT)
  }.snapshot()

  private fun agent(
    occupation: Occupation,
    hour: Int,
    day: Long,
    tendedOn: Long? = null,
    withRoute: Boolean = true,
  ): SimpleAgent {
    val memory = Blackboard().apply {
      set(TownsfolkDomain.POSITION, home, Blackboard.PERMANENT)
      set(TownsfolkDomain.HOME_POSITION, home, Blackboard.PERMANENT)
      set(TownsfolkDomain.HOME_BUILDING, HOUSE, Blackboard.PERMANENT)
      set(TownsfolkDomain.MINUTE_OF_DAY, hour * HourWindow.MINUTES_PER_HOUR, Blackboard.PERMANENT)
      set(TownsfolkDomain.DAY_INDEX, day, Blackboard.PERMANENT)
      set(TownsfolkDomain.WANDER_RADIUS, TownsfolkDomain.DEFAULT_LOITER_RADIUS, Blackboard.PERMANENT)
      set(TownsfolkDomain.TIREDNESS, 20, Blackboard.PERMANENT)
      set(TownsfolkDomain.RESTLESSNESS, 100, Blackboard.PERMANENT)
      set(TownsfolkDomain.OCCUPATION, occupation, Blackboard.PERMANENT)
      if (withRoute) set(TownsfolkDomain.LANTERN_ROUTE, route, Blackboard.PERMANENT)
      tendedOn?.let { set(TownsfolkDomain.LANTERNS_TENDED_ON_DAY, it, Blackboard.PERMANENT) }
    }
    return SimpleAgent(
      name = "lamplighter",
      goals = TownsfolkDomain.Goals.ALL,
      memory = memory,
      actionResolver = TownsfolkDomainFixture.resolver(),
    )
  }

  private companion object {
    const val HOUSE = 4_242L
  }
}
