package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.ai.core.agent.Agent
import net.bestia.zone.ai.core.agent.SimpleAgent
import net.bestia.zone.ai.core.planner.PlanExecutor
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the peaceful goals end to end: eating known vegetation, sleeping, returning home when too far
 * away, and — now that idleness is an ordinary drive rather than a special case — wandering when restless.
 */
class NonAggroScenarioTest {

  private val planner = Planner()
  private val executor = PlanExecutor()
  private val home = Vec3L(0, 0, 0)

  private val peacefulGoals = listOf(
    BestiaDomain.Goals.EAT_VEGETATION,
    BestiaDomain.Goals.SLEEP,
    BestiaDomain.Goals.RETURN_HOME,
    BestiaDomain.Goals.WANDER,
  )

  private fun freshMemory(): Blackboard = Blackboard().apply {
    set(BestiaDomain.POSITION, home)
    set(BestiaDomain.HOME_POSITION, home, Blackboard.PERMANENT)
    set(BestiaDomain.WANDER_RADIUS, 5L, Blackboard.PERMANENT)
    set(BestiaDomain.HUNGER_THRESHOLD, 85, Blackboard.PERMANENT)
    set(BestiaDomain.TIREDNESS_THRESHOLD, 80, Blackboard.PERMANENT)
    set(BestiaDomain.RESTLESS_THRESHOLD, 60, Blackboard.PERMANENT)
    set(BestiaDomain.HUNGER, 20)
    set(BestiaDomain.TIREDNESS, 10)
    set(BestiaDomain.RESTLESSNESS, 0)
  }

  private fun agentWith(memory: Blackboard): Agent = SimpleAgent(
    name = "test-bestia",
    goals = peacefulGoals,
    memory = memory,
    actionResolver = BestiaDomainFixture.resolver(
      listOf("returnHome", "walkToVegetation", "eatVegetation", "sleep", "wander"),
    ),
  )

  @Test
  fun `walks to and eats known vegetation when hungry`() {
    val memory = freshMemory()
    memory.set(BestiaDomain.HUNGER, 90)
    memory.set(BestiaDomain.KNOWN_VEGETATION, listOf(VegetationMemory(Vec3L(3, 0, 0), discoveredAtMs = 0L)))
    val agent = agentWith(memory)
    val world = Blackboard()

    val plan = planner.makePlanForAgent(agent, world)
    assertNotNull(plan)
    assertEquals(2, plan.actions.size)
    assertTrue(plan.actions.first().name.startsWith("walkToVegetation"))
    assertEquals("eatVegetation", plan.actions.last().name)

    executor.execute(plan, agent, world)
    assertTrue((memory.get(BestiaDomain.HUNGER) ?: 100) <= 15)
    assertTrue(memory.get(BestiaDomain.KNOWN_VEGETATION).orEmpty().isEmpty())
  }

  @Test
  fun `sleeps when tired regardless of position`() {
    val memory = freshMemory()
    memory.set(BestiaDomain.TIREDNESS, 95)
    val agent = agentWith(memory)
    val world = Blackboard()

    val plan = planner.makePlanForAgent(agent, world)
    assertEquals(listOf("sleep"), plan?.actions?.map { it.name })

    executor.execute(plan!!, agent, world)
    assertTrue((memory.get(BestiaDomain.TIREDNESS) ?: 100) <= 20)
  }

  @Test
  fun `returns home once it has wandered further than its wander radius`() {
    val memory = freshMemory()
    memory.set(BestiaDomain.POSITION, Vec3L(20, 0, 0))
    val agent = agentWith(memory)
    val world = Blackboard()

    val plan = planner.makePlanForAgent(agent, world)
    assertEquals(listOf("returnHome"), plan?.actions?.map { it.name })
  }

  @Test
  fun `returning home does not overwrite its believed position, because walking is not arriving`() {
    val memory = freshMemory()
    val away = Vec3L(20, 0, 0)
    memory.set(BestiaDomain.POSITION, away)
    val agent = agentWith(memory)
    val world = Blackboard()

    val plan = planner.makePlanForAgent(agent, world)
    executor.execute(plan!!, agent, world)

    // The planner had to imagine standing at home in order to find the plan at all, but position is an
    // observation: only perception may write it. Persisting the hypothesis is exactly the bug the old
    // executor had — a creature that decided to walk home believed it was already there.
    assertEquals(away, memory.get(BestiaDomain.POSITION))
  }

  @Test
  fun `wanders when restless, as an ordinary goal rather than a fallback`() {
    val memory = freshMemory()
    memory.set(BestiaDomain.RESTLESSNESS, 90)
    val agent = agentWith(memory)
    val world = Blackboard()

    val plan = planner.makePlanForAgent(agent, world)
    assertEquals(listOf("wander"), plan?.actions?.map { it.name })
    assertEquals("Wander", plan?.goal?.name)

    executor.execute(plan!!, agent, world)
    assertTrue((memory.get(BestiaDomain.RESTLESSNESS) ?: 100) <= 20, "wandering should spend restlessness")
  }

  @Test
  fun `has nothing to do at all when every drive is low`() {
    val memory = freshMemory()
    val agent = agentWith(memory)

    // No goal is available, so there is nothing to plan — and that is now a legitimate resting state
    // rather than something needing an escape hatch, because the drive system will raise restlessness
    // until the wander goal becomes available on its own.
    assertEquals(null, planner.makePlanForAgent(agent, Blackboard()))
  }
}
