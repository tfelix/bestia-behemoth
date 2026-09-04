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
 * remembered-effective attack is cheapest once several are in range, and standing and fighting however badly
 * hurt the bestia is.
 *
 * That last one used to be the opposite assertion — a wounded creature was expected to break off and run.
 * There is no flee goal any more, so the cases that asserted it now assert that being hurt changes nothing
 * about whether a creature defends itself.
 */
class AggroScenarioTest {

  private val planner = Planner()
  private val executor = PlanExecutor()

  private val combatGoals = listOf(
    BestiaDomain.Goals.KILL_ATTACKER,
    BestiaDomain.Goals.KILL_ENEMY,
  )

  private fun aggroMemory(
    targetPosition: Vec3L,
    archetype: String = "human",
    healthPct: Int = 100,
  ): Blackboard = Blackboard().apply {
    set(BestiaDomain.POSITION, Vec3L(0, 0, 0))
    set(BestiaDomain.HOME_POSITION, Vec3L(0, 0, 0), Blackboard.PERMANENT)
    set(BestiaDomain.MELEE_RANGE, 1L, Blackboard.PERMANENT)
    set(BestiaDomain.AGGRESSION, 80, Blackboard.PERMANENT)
    set(BestiaDomain.HEALTH_PCT, healthPct)
    set(BestiaDomain.IS_AGGRO, true)
    set(BestiaDomain.ENEMY_IN_SIGHT, true)
    set(BestiaDomain.TARGET_ID, 42L)
    set(BestiaDomain.TARGET_ARCHETYPE, archetype)
    set(BestiaDomain.TARGET_POSITION, targetPosition)
  }

  @Test
  fun `walks into melee range before attacking when the attacker is far away`() {
    val memory = aggroMemory(targetPosition = Vec3L(5, 0, 0))
    val attacks = listOf(AttackDefinition(id = "claw", range = 1))
    val agent = SimpleAgent(
      name = "wolf",
      goals = combatGoals,
      memory = memory,
      actionResolver = BestiaDomainFixture.resolver(listOf("approachTarget", "attack"), attacks),
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
        listOf("approachTarget", "attack"),
        listOf(AttackDefinition(id = "claw", range = 1)),
      ),
    )

    val plan = planner.makePlanForAgent(agent, Blackboard())
    assertEquals("KillAttacker", plan?.goal?.name)
  }

  @Test
  fun `still fights back when nearly dead, rather than breaking off`() {
    val memory = aggroMemory(targetPosition = Vec3L(1, 0, 0), healthPct = 5)
    val agent = SimpleAgent(
      name = "wolf",
      goals = combatGoals,
      memory = memory,
      actionResolver = BestiaDomainFixture.resolver(
        listOf("approachTarget", "attack"),
        listOf(AttackDefinition(id = "claw", range = 1)),
      ),
    )
    val world = Blackboard()

    // The inverse of the flee test this replaces. At 5% health there is nothing left to prefer over
    // retaliating, so the creature swings back instead of running — which is the whole point of removing the
    // mechanic: a mob that bolts on the first hit cannot be fought.
    val plan = planner.makePlanForAgent(agent, world)
    assertEquals("KillAttacker", plan?.goal?.name)
    assertEquals(listOf("attack(claw)"), plan?.actions?.map { it.name })

    executor.execute(plan!!, agent, world)
    assertEquals(true, memory.get(BestiaDomain.TARGET_DEAD))
  }

  @Test
  fun `retaliation outranks the drives, so a hungry tired mob does not wander off mid-fight`() {
    val memory = aggroMemory(targetPosition = Vec3L(1, 0, 0), healthPct = 30).apply {
      set(BestiaDomain.HUNGER, 100)
      set(BestiaDomain.HUNGER_THRESHOLD, 60, Blackboard.PERMANENT)
      set(BestiaDomain.TIREDNESS, 100)
      set(BestiaDomain.TIREDNESS_THRESHOLD, 70, Blackboard.PERMANENT)
    }

    val agent = SimpleAgent(
      name = "blob",
      goals = combatGoals + listOf(BestiaDomain.Goals.EAT_VEGETATION, BestiaDomain.Goals.SLEEP),
      memory = memory,
      actionResolver = BestiaDomainFixture.resolver(
        listOf("approachTarget", "attack", "sleep"),
        listOf(AttackDefinition(id = "claw", range = 1)),
      ),
    )

    // KillAttacker's flat 95 beats EatVegetation (80 at its maximum) and Sleep (90), which is what the
    // passive-wanderer profile relies on now that its base_priority override is gone: that override used to
    // hold retaliation down at 60, below both of these, because fleeing was the real answer to being hurt.
    val plan = planner.makePlanForAgent(agent, Blackboard())
    assertEquals("KillAttacker", plan?.goal?.name)
  }

  @Test
  fun `considers unprovoked aggression even while wounded`() {
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

    // KillEnemy used to be gated off below the flee threshold, so exactly one of hunting and running was
    // ever available. With nothing left to hand over to, that gate would have left a wounded hunter with its
    // target in plain sight and no goal at all — so it is gone, and health only scales the priority now.
    val plan = planner.makePlanForAgent(agent, Blackboard())
    assertEquals("KillEnemy", plan?.goal?.name)
    assertEquals(listOf("approachTarget", "attack(claw)"), plan?.actions?.map { it.name })
  }
}
