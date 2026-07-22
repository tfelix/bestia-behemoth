package net.bestia.zone.ai.goap2.bestia

import net.bestia.zone.ai.goap2.agent.Agent
import net.bestia.zone.ai.goap2.planner.PlanExecutor
import net.bestia.zone.ai.goap2.planner.Planner
import net.bestia.zone.ai.goap2.state.Blackboard
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the NON_AGGRO goal set end to end: eating known vegetation, sleeping, returning home
 * when too far away, and falling back to wandering when nothing is pressing.
 */
class NonAggroScenarioTest {

  private val planner = Planner()
  private val executor = PlanExecutor()
  private val home = Vec3L(0, 0, 0)

  private fun freshMemory(): Blackboard = Blackboard().apply {
    set(BestiaDomain.POSITION, home)
    set(BestiaDomain.HOME_POSITION, home, Blackboard.PERMANENT)
    set(BestiaDomain.WANDER_RADIUS, 5L, Blackboard.PERMANENT)
    set(BestiaDomain.HUNGER_THRESHOLD, 85, Blackboard.PERMANENT)
    set(BestiaDomain.TIREDNESS_THRESHOLD, 80, Blackboard.PERMANENT)
    set(BestiaDomain.HUNGER, 20)
    set(BestiaDomain.TIREDNESS, 10)
  }

  private fun agentWith(memory: Blackboard): Agent = Agent(
    name = "test-bestia",
    goals = BestiaDomain.Goals.NON_AGGRO,
    memory = memory,
    actionResolver = BestiaDomain.resolver(listOf("returnHome", "walkToVegetation", "eatVegetation", "sleep")),
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

    executor.execute(plan!!, agent, world)
    assertEquals(home, memory.get(BestiaDomain.POSITION))
  }

  @Test
  fun `has nothing pressing to plan for and falls back to wandering`() {
    val memory = freshMemory()
    val agent = agentWith(memory)
    val world = Blackboard()

    assertNull(planner.makePlanForAgent(agent, world))

    val state = agent.snapshotState(world)
    val wanderAction = BestiaDomain.fallbackWander(state)
    assertNotNull(wanderAction)
    assertTrue(wanderAction.name.startsWith("wanderTo"))
  }
}
