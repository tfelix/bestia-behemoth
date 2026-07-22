package net.bestia.zone.ai.goap2.bestia

import net.bestia.zone.ai.goap2.agent.Agent
import net.bestia.zone.ai.goap2.planner.PlanExecutor
import net.bestia.zone.ai.goap2.planner.Planner
import net.bestia.zone.ai.goap2.state.Blackboard
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Exercises the AGGRO goal: closing to melee range before attacking, and preferring whichever
 * remembered-effective attack is cheapest once several are in range.
 */
class AggroScenarioTest {

  private val planner = Planner()
  private val executor = PlanExecutor()

  private fun aggroMemory(targetPosition: Vec3L, archetype: String = "human"): Blackboard = Blackboard().apply {
    set(BestiaDomain.POSITION, Vec3L(0, 0, 0))
    set(BestiaDomain.HOME_POSITION, Vec3L(0, 0, 0), Blackboard.PERMANENT)
    set(BestiaDomain.MELEE_RANGE, 1L, Blackboard.PERMANENT)
    set(BestiaDomain.IS_AGGRO, true)
    set(BestiaDomain.TARGET_ID, "attacker-1")
    set(BestiaDomain.TARGET_ARCHETYPE, archetype)
    set(BestiaDomain.TARGET_POSITION, targetPosition)
  }

  @Test
  fun `walks into melee range before attacking when the attacker is far away`() {
    val memory = aggroMemory(targetPosition = Vec3L(5, 0, 0))
    val attacks = listOf(AttackDefinition(id = "claw", range = 1))
    val agent = Agent(
      name = "wolf",
      goals = BestiaDomain.Goals.AGGRO,
      memory = memory,
      actionResolver = BestiaDomain.resolver(listOf("approachTarget", "attack"), attacks),
    )
    val world = Blackboard()

    val plan = planner.makePlanForAgent(agent, world)
    assertEquals(listOf("approachTarget", "attack(claw)"), plan?.actions?.map { it.name })

    executor.execute(plan!!, agent, world)
    assertEquals(true, memory.get(BestiaDomain.TARGET_DEAD))
  }

  @Test
  fun `prefers the attack remembered as more effective against this archetype`() {
    val memory = aggroMemory(targetPosition = Vec3L(0, 0, 0), archetype = "golem")
    AttackEffectiveness.record(memory, EffectivenessKey("golem", "slash"), observed = 0.1)
    AttackEffectiveness.record(memory, EffectivenessKey("golem", "fireBolt"), observed = 0.9)

    val attacks = listOf(
      AttackDefinition(id = "slash", range = 1, baseCost = 5f),
      AttackDefinition(id = "fireBolt", range = 4, baseCost = 5f),
    )
    val agent = Agent(
      name = "golem-hunter",
      goals = BestiaDomain.Goals.AGGRO,
      memory = memory,
      actionResolver = BestiaDomain.resolver(listOf("approachTarget", "attack"), attacks),
    )

    val plan = planner.makePlanForAgent(agent, Blackboard())
    assertNotNull(plan)
    assertEquals("attack(fireBolt)", plan.actions.single().name)
  }
}
