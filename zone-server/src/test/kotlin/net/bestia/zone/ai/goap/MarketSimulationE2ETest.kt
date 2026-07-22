package net.bestia.zone.ai.goap

import net.bestia.zone.ai.goap.MarketDomain.GOLD
import net.bestia.zone.ai.goap.MarketDomain.INVENTORY
import net.bestia.zone.ai.goap.MarketDomain.MARKET
import net.bestia.zone.ai.goap.MarketDomain.POSITION
import net.bestia.zone.ai.goap.MarketDomain.SATIATION
import net.bestia.zone.ai.goap.MarketDomain.TIREDNESS
import net.bestia.zone.ai.goap2.agent.Agent
import net.bestia.zone.ai.goap2.goal.Goal
import net.bestia.zone.ai.goap2.goal.InverseLinearCurve
import net.bestia.zone.ai.goap2.goal.LinearCurve
import net.bestia.zone.ai.goap2.goal.priority
import net.bestia.zone.ai.goap2.planner.PlanExecutor
import net.bestia.zone.ai.goap2.planner.Planner
import net.bestia.zone.ai.goap2.precondition.Preconditions
import net.bestia.zone.ai.goap2.state.Blackboard
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * End-to-end walkthrough of the full sense -> plan -> act loop, run over a few
 * simulated "days" for one villager. Run with `./gradlew test --tests
 * MarketSimulationE2ETest` and watch the console: [Planner] logs which goal it
 * picked and why, and [PlanExecutor] logs every action as it is carried out and
 * how it changed the villager's memory.
 *
 * The scenario is deliberately driven to exhaustion: the villager starts with
 * only enough gold for two meals, so day three demonstrates what the logs look
 * like when no plan can be found at all.
 */
class MarketSimulationE2ETest {

  private val log = LoggerFactory.getLogger(MarketSimulationE2ETest::class.java)

  private val eatGoal = Goal(
    name = "Eat",
    priority = priority(base = 80f) { consider(InverseLinearCurve(SATIATION)) },
    availability = Preconditions.atMost(SATIATION, 99),
    desiredState = listOf(Preconditions.atLeast(SATIATION, 80)),
  )

  private val getRichGoal = Goal(
    name = "GetRich",
    priority = priority(base = 40f) { consider(LinearCurve(GOLD)) },
    availability = Preconditions.atLeast(GOLD, 0),
    desiredState = listOf(Preconditions.atLeast(GOLD, 1_000)),
  )

  private val sleepGoal = Goal(
    name = "Sleep",
    priority = priority(base = 80f) { consider(LinearCurve(TIREDNESS)) },
    availability = Preconditions.atLeast(TIREDNESS, 80),
    desiredState = listOf(Preconditions.atMost(TIREDNESS, 20)),
  )

  @Test
  fun `villager eats until the gold runs out, then planning fails`() {
    val memory = Blackboard().apply {
      set(POSITION, MarketDomain.HOME)
      set(GOLD, 10)
      set(SATIATION, 20)
      set(INVENTORY, emptySet())
    }
    val world = Blackboard()
    val agent = Agent(
      name = "villager",
      goals = listOf(eatGoal, getRichGoal, sleepGoal),
      memory = memory,
      actionResolver = MarketDomain.resolver
    )

    val planner = Planner()
    val executor = PlanExecutor()

    // Day 1: hungry and flush with cash -> must travel to the market first.
    log.info("=== Day 1: satiation=20, gold=10 ===")
    val day1 = planner.makePlanForAgent(agent, world)
    assertEquals(listOf("walkTo(market)", "buyItem(food)", "eat"), day1?.actions?.map { it.name })
    executor.execute(day1!!, memory, world)
    assertEquals(100, memory.get(SATIATION))
    assertEquals(5, memory.get(GOLD))
    assertEquals(MARKET, memory.get(POSITION))

    // Day 2: was suddenly super sleep.
    memory.set(TIREDNESS, 90)
    log.info("=== Day 2: satiation=15, gold=5, tiredness=90 ===")
    val day2 = planner.makePlanForAgent(agent, world)
    executor.execute(day2!!, memory, world)
    assertEquals(40, memory.get(SATIATION))
    assertEquals(5, memory.get(GOLD))

    // Day 3: hungry again, still at the market and just enough gold for one more meal.
    log.info("=== Day 3: satiation=70, gold=5 ===")
    val day3 = planner.makePlanForAgent(agent, world)
    assertEquals(listOf("walkTo(market)", "buyItem(food)", "eat"), day3?.actions?.map { it.name })
    executor.execute(day3!!, memory, world)
    assertEquals(100, memory.get(SATIATION))
    assertEquals(0, memory.get(GOLD))

    // Day 4: hungry a third time, but out of gold -> no action leads to the goal.
    memory.set(SATIATION, 10)
    log.info("=== Day 4: satiation=10, gold=0 ===")
    val day4 = planner.makePlanForAgent(agent, world)
    assertNull(day4, "no plan should be found once the villager can no longer afford food")
  }
}
