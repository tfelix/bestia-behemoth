package net.bestia.zone.ai.domain.townsfolk

import net.bestia.zone.ai.core.agent.SimpleAgent
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

  /** Far enough from home that GoHome would fight the shift if the priorities were the other way round. */
  private val post = Vec3L(100 + TownsfolkDomain.DEFAULT_LOITER_RADIUS + 20, 100, 0)

  private val guard = Occupation("guard", "guard", "barracks", HourWindow(6, 18), HourWindow(22, 6))

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

  @Test
  fun `on shift and away from the post, the plan is to go there and stand it out`() {
    val plan = planner.makePlanForAgent(
      townsperson(at = home, hour = 9, occupation = guard, post = post),
      Blackboard()
    )

    assertEquals("WorkShift", plan?.goal?.name)
    assertEquals(listOf("goToWork", "workShift"), plan?.actions?.map { it.name })
  }

  @Test
  fun `work outranks being pulled home from a post outside the loiter radius`() {
    // The post is deliberately further from home than the loiter radius, which is the ordinary case for a
    // guard. GoHome would otherwise turn them round the moment they arrived and they would never work.
    assertTrue(
      post.distance(home) > TownsfolkDomain.DEFAULT_LOITER_RADIUS,
      "this case only means something if the post is out of loitering range"
    )

    val plan = planner.makePlanForAgent(
      townsperson(at = post, hour = 9, occupation = guard, post = post),
      Blackboard()
    )

    assertEquals("WorkShift", plan?.goal?.name)
  }

  @Test
  fun `the shift is not worked twice in one day`() {
    val plan = planner.makePlanForAgent(
      townsperson(at = post, hour = 9, occupation = guard, post = post, workedOnDay = TODAY),
      Blackboard()
    )

    assertNotEquals("WorkShift", plan?.goal?.name, "today's shift is already seen through")
  }

  @Test
  fun `an occupation with nowhere to work does something else rather than nothing`() {
    // AiThinkSystem plans for the top goal only, so a goal that is available and unplannable leaves the
    // agent with no plan at all. A guard in a village with no barracks must loiter, not freeze.
    val plan = planner.makePlanForAgent(
      townsperson(at = home, hour = 9, occupation = guard, post = null),
      Blackboard()
    )

    assertNotNull(plan)
    assertNotEquals("WorkShift", plan.goal.name)
  }

  @Test
  fun `somebody with no shift never goes to work`() {
    val child = Occupation("child", "child", businessType = null, shift = null, rest = HourWindow(21, 7))

    for (hour in 0 until 24) {
      val plan = planner.makePlanForAgent(
        townsperson(at = home, hour = hour, occupation = child, post = post),
        Blackboard()
      )

      assertNotEquals("WorkShift", plan?.goal?.name, "a child was sent to work at $hour:00")
    }
  }

  @Test
  fun `bedtime follows the occupation rather than the archetype`() {
    // An innkeeper is still up at eleven and still in bed at half past six, and the commoner is the
    // reverse of both. Perception reads the same window to decide when to clear RESTED, so the two cannot
    // disagree - see TownsfolkDomain.restingWindowFor.
    val innkeeper = Occupation("innkeeper", "innkeeper", "inn", HourWindow(10, 23), HourWindow(0, 7))
    val sleep = TownsfolkDomain.Goals.BY_NAME.getValue("Sleep")

    val lateNight = townsperson(at = home, hour = 22, occupation = innkeeper, post = post).memory.snapshot()
    assertFalse(sleep.isAvailable(lateNight), "an innkeeper does not shut at ten")

    val earlyMorning = townsperson(at = home, hour = 6, occupation = innkeeper, post = post).memory.snapshot()
    assertTrue(sleep.isAvailable(earlyMorning), "and is still asleep when the commoner is up")
  }

  private fun townsperson(
    at: Vec3L,
    hour: Int,
    tiredness: Int = 30,
    restlessness: Int = 100,
    rested: Boolean = false,
    occupation: Occupation? = null,
    post: Vec3L? = null,
    workedOnDay: Long? = null,
  ): SimpleAgent {
    val memory = Blackboard().apply {
      set(TownsfolkDomain.POSITION, at, Blackboard.PERMANENT)
      set(TownsfolkDomain.HOME_POSITION, home, Blackboard.PERMANENT)
      set(TownsfolkDomain.HOUR_OF_DAY, hour, Blackboard.PERMANENT)
      set(TownsfolkDomain.DAY_INDEX, TODAY, Blackboard.PERMANENT)
      set(TownsfolkDomain.WANDER_RADIUS, TownsfolkDomain.DEFAULT_LOITER_RADIUS, Blackboard.PERMANENT)
      set(TownsfolkDomain.TIREDNESS, tiredness, Blackboard.PERMANENT)
      set(TownsfolkDomain.RESTLESSNESS, restlessness, Blackboard.PERMANENT)
      if (rested) set(TownsfolkDomain.RESTED, true, Blackboard.PERMANENT)
      occupation?.let { set(TownsfolkDomain.OCCUPATION, it, Blackboard.PERMANENT) }
      post?.let { set(TownsfolkDomain.WORK_POSITION, it, Blackboard.PERMANENT) }
      workedOnDay?.let { set(TownsfolkDomain.WORKED_ON_DAY, it, Blackboard.PERMANENT) }
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
    const val TODAY = 40L
  }
}
