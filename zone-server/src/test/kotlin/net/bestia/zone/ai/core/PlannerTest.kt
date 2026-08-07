package net.bestia.zone.ai.core

import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.core.agent.SimpleAgent
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.goal.Goal
import net.bestia.zone.ai.core.goal.priority
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.precondition.Preconditions
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.StateKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Search-level tests for the forward A* planner, over a deliberately tiny abstract domain rather than a game
 * one, so a failure here means the search is wrong and not that a bestia goal is mistuned.
 *
 * These carry over what the old symbolic planner's tests covered — chaining, single steps, unreachable goals,
 * already-satisfied goals — onto the typed core, and add the cases the old boolean-only state could not
 * express at all: cost-based preference and goal selection by priority.
 */
class PlannerTest {

  private val planner = Planner()

  private val doorOpen = StateKey<Boolean>("doorOpen")
  private val hasKey = StateKey<Boolean>("hasKey")
  private val outside = StateKey<Boolean>("outside")
  private val gold = StateKey<Int>("gold")

  private val takeKey = Action(
    name = "takeKey",
    effects = listOf(Effects.set(hasKey, true)),
  )

  private val openDoor = Action(
    name = "openDoor",
    preconditions = listOf(Preconditions.equalTo(hasKey, true)),
    effects = listOf(Effects.set(doorOpen, true)),
  )

  private val walkOut = Action(
    name = "walkOut",
    preconditions = listOf(Preconditions.equalTo(doorOpen, true)),
    effects = listOf(Effects.set(outside, true)),
  )

  private fun escapeGoal(base: Float = 50f) = Goal(
    name = "Escape",
    priority = priority(base = base),
    availability = Precondition { true },
    desiredState = listOf(Preconditions.equalTo(outside, true)),
  )

  private fun agent(
    goals: List<Goal>,
    actions: List<Action>,
    memory: Blackboard = Blackboard(),
  ) = SimpleAgent(
    name = "test",
    goals = goals,
    memory = memory,
    actionResolver = ActionResolver { actions },
  )

  @Test
  fun `chains actions through their preconditions`() {
    val plan = planner.makePlanForAgent(
      agent(listOf(escapeGoal()), listOf(walkOut, openDoor, takeKey)),
      Blackboard(),
    )

    // Deliberately supplied out of order: the chain comes from the preconditions, not the list.
    assertEquals(listOf("takeKey", "openDoor", "walkOut"), plan?.actions?.map { it.name })
  }

  @Test
  fun `finds a single step plan when the rest already holds`() {
    val memory = Blackboard().apply { set(doorOpen, true) }
    val plan = planner.makePlanForAgent(agent(listOf(escapeGoal()), listOf(walkOut, openDoor, takeKey), memory), Blackboard())

    assertEquals(listOf("walkOut"), plan?.actions?.map { it.name })
  }

  @Test
  fun `returns null when no chain of actions can reach the goal`() {
    // No action produces `outside` without the door, and nothing can open it without a key.
    val plan = planner.makePlanForAgent(agent(listOf(escapeGoal()), listOf(openDoor, walkOut)), Blackboard())

    assertNull(plan)
  }

  @Test
  fun `does not select a goal that is already satisfied`() {
    val memory = Blackboard().apply { set(outside, true) }
    val plan = planner.makePlanForAgent(agent(listOf(escapeGoal()), listOf(walkOut), memory), Blackboard())

    // Not an empty plan but no plan at all: the goal never becomes a candidate, which is what stops an
    // agent re-pursuing something it has already achieved.
    assertNull(plan)
  }

  @Test
  fun `prefers the cheaper of two routes to the same outcome`() {
    val cheapExit = Action(
      name = "cheapExit",
      effects = listOf(Effects.set(outside, true)),
      cost = { 1f },
    )
    val expensiveExit = Action(
      name = "expensiveExit",
      effects = listOf(Effects.set(outside, true)),
      cost = { 99f },
    )

    val plan = planner.makePlanForAgent(
      agent(listOf(escapeGoal()), listOf(expensiveExit, cheapExit)),
      Blackboard(),
    )

    assertEquals(listOf("cheapExit"), plan?.actions?.map { it.name })
  }

  @Test
  fun `picks the highest priority available goal`() {
    val getRich = Goal(
      name = "GetRich",
      priority = priority(base = 90f),
      availability = Precondition { true },
      desiredState = listOf(Preconditions.atLeast(gold, 10)),
    )
    val earn = Action(name = "earn", effects = listOf(Effects.set(gold, 10)))

    val plan = planner.makePlanForAgent(
      agent(listOf(escapeGoal(base = 50f), getRich), listOf(walkOut, openDoor, takeKey, earn)),
      Blackboard(),
    )

    assertEquals("GetRich", plan?.goal?.name)
  }

  @Test
  fun `skips a goal that is not currently available however urgent it would be`() {
    val unavailable = Goal(
      name = "Unavailable",
      priority = priority(base = 1000f),
      availability = Precondition { false },
      desiredState = listOf(Preconditions.equalTo(outside, true)),
    )

    val plan = planner.makePlanForAgent(
      agent(listOf(unavailable, escapeGoal()), listOf(walkOut, openDoor, takeKey)),
      Blackboard(),
    )

    assertEquals("Escape", plan?.goal?.name)
  }

  @Test
  fun `gives up rather than hanging when the search space cannot satisfy the goal`() {
    // A goal nothing can satisfy, with actions that keep generating fresh states: the iteration cap is what
    // guarantees this returns instead of expanding forever.
    val counter = StateKey<Int>("counter")
    val increment = Action(
      name = "increment",
      effects = listOf(Effects.modify(counter) { (it ?: 0) + 1 }),
    )
    val impossible = Goal(
      name = "Impossible",
      priority = priority(base = 10f),
      availability = Precondition { true },
      desiredState = listOf(Preconditions.equalTo(outside, true)),
    )

    val bounded = Planner(maxIterations = 100)
    assertNull(bounded.makePlanForAgent(agent(listOf(impossible), listOf(increment)), Blackboard()))
  }

  @Test
  fun `reports the summed cost of the plan it found`() {
    val plan = planner.makePlanForAgent(
      agent(listOf(escapeGoal()), listOf(walkOut, openDoor, takeKey)),
      Blackboard(),
    )

    assertNotNull(plan)
    assertTrue(plan.totalCost > 0f)
  }
}
