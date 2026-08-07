package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.ai.core.agent.SimpleAgent
import net.bestia.zone.ai.core.planner.PlanExecutor
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Exercises the combat goals: closing to melee range before attacking, preferring whichever
 * remembered-effective attack is cheapest once several are in range, and choosing between retaliating and
 * running away depending on how badly hurt the bestia is.
 */
class AggroScenarioTest {

  private val planner = Planner()
  private val executor = PlanExecutor()

  private val combatGoals = listOf(
    BestiaDomain.Goals.KILL_ATTACKER,
    BestiaDomain.Goals.KILL_ENEMY,
    BestiaDomain.Goals.FLEE,
  )

  private fun aggroMemory(
    targetPosition: Vec3L,
    archetype: String = "human",
    healthPct: Int = 100,
  ): Blackboard = Blackboard().apply {
    set(BestiaDomain.POSITION, Vec3L(0, 0, 0))
    set(BestiaDomain.HOME_POSITION, Vec3L(0, 0, 0), Blackboard.PERMANENT)
    set(BestiaDomain.MELEE_RANGE, 1L, Blackboard.PERMANENT)
    set(BestiaDomain.FLEE_THRESHOLD_PCT, 35, Blackboard.PERMANENT)
    set(BestiaDomain.AGGRESSION, 80, Blackboard.PERMANENT)
    set(BestiaDomain.HEALTH_PCT, healthPct)
    set(BestiaDomain.IS_AGGRO, true)
    set(BestiaDomain.ENEMY_IN_SIGHT, true)
    set(BestiaDomain.TARGET_ID, 42L)
    set(BestiaDomain.TARGET_ARCHETYPE, archetype)
    set(BestiaDomain.TARGET_POSITION, targetPosition)
    set(BestiaDomain.THREAT_POSITION, targetPosition)
  }

  @Test
  fun `walks into melee range before attacking when the attacker is far away`() {
    val memory = aggroMemory(targetPosition = Vec3L(5, 0, 0))
    val attacks = listOf(AttackDefinition(id = "claw", range = 1))
    val agent = SimpleAgent(
      name = "wolf",
      goals = combatGoals,
      memory = memory,
      actionResolver = BestiaDomainFixture.resolver(listOf("approachTarget", "attack", "flee"), attacks),
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
    val agent = SimpleAgent(
      name = "golem-hunter",
      goals = combatGoals,
      memory = memory,
      actionResolver = BestiaDomainFixture.resolver(listOf("approachTarget", "attack"), attacks),
    )

    val plan = planner.makePlanForAgent(agent, Blackboard())
    assertNotNull(plan)
    assertEquals("attack(fireBolt)", plan.actions.single().name)
  }

  @Test
  fun `fights while healthy`() {
    val memory = aggroMemory(targetPosition = Vec3L(1, 0, 0), healthPct = 100)
    val agent = SimpleAgent(
      name = "wolf",
      goals = combatGoals,
      memory = memory,
      actionResolver = BestiaDomainFixture.resolver(
        listOf("approachTarget", "attack", "flee"),
        listOf(AttackDefinition(id = "claw", range = 1)),
      ),
    )

    val plan = planner.makePlanForAgent(agent, Blackboard())
    assertEquals("KillAttacker", plan?.goal?.name)
  }

  @Test
  fun `flees instead of fighting once hurt past its threshold`() {
    val memory = aggroMemory(targetPosition = Vec3L(1, 0, 0), healthPct = 20)
    val agent = SimpleAgent(
      name = "wolf",
      goals = combatGoals,
      memory = memory,
      actionResolver = BestiaDomainFixture.resolver(
        listOf("approachTarget", "attack", "flee"),
        listOf(AttackDefinition(id = "claw", range = 1)),
      ),
    )
    val world = Blackboard()

    // Flee outranks retaliation at 20% health because its base priority is higher *and* its urgency curve
    // rises as health falls, while KillEnemy is not even available below the threshold.
    val plan = planner.makePlanForAgent(agent, world)
    assertEquals("Flee", plan?.goal?.name)
    assertEquals(listOf("flee"), plan?.actions?.map { it.name })

    executor.execute(plan!!, agent, world)
    assertEquals(true, memory.get(BestiaDomain.SAFE))
  }

  @Test
  fun `does not consider unprovoked aggression while wounded`() {
    val memory = aggroMemory(targetPosition = Vec3L(3, 0, 0), healthPct = 20)
    memory.set(BestiaDomain.IS_AGGRO, false)

    val agent = SimpleAgent(
      name = "wolf",
      goals = listOf(BestiaDomain.Goals.KILL_ENEMY),
      memory = memory,
      actionResolver = BestiaDomainFixture.resolver(
        listOf("approachTarget", "attack"),
        listOf(AttackDefinition(id = "claw", range = 1)),
      ),
    )

    // KillEnemy and Flee share one threshold from opposite sides, so a wounded creature is never offered
    // both — which is what stops it flip-flopping between charging and bolting.
    assertEquals(null, planner.makePlanForAgent(agent, Blackboard()))
  }
}
