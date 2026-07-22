package net.bestia.zone.ai.goap2.bestia

import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.action.ActionResolver
import net.bestia.zone.ai.goap2.agent.Agent
import net.bestia.zone.ai.goap2.effect.Effects
import net.bestia.zone.ai.goap2.goal.Goal
import net.bestia.zone.ai.goap2.goal.priority
import net.bestia.zone.ai.goap2.planner.PlanExecutor
import net.bestia.zone.ai.goap2.planner.Planner
import net.bestia.zone.ai.goap2.precondition.Precondition
import net.bestia.zone.ai.goap2.precondition.Preconditions
import net.bestia.zone.ai.goap2.state.Blackboard
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the three-tier memory model actually shares, in both directions: [BestiaDomain.KNOWN_VEGETATION]
 * (team-scoped) cascades from one agent's own action into the shared pack board via [PlanExecutor],
 * and [BestiaDomain.ATTACK_EFFECTIVENESS] (world-scoped) is visible to any agent at all, even across
 * different packs, once it lands on the single world board.
 */
class SharedMemoryTest {

  private val planner = Planner()
  private val executor = PlanExecutor()

  @Test
  fun `vegetation one packmate discovers becomes visible to another via the shared team board`() {
    val team = Blackboard()
    val world = Blackboard()
    val spot = VegetationMemory(Vec3L(2, 0, 0), discoveredAtMs = 0L)

    val discoverGoal = Goal(
      name = "TestDiscoverVegetation",
      priority = priority(base = 50f),
      availability = Precondition { true },
      desiredState = listOf(
        Preconditions.satisfies(BestiaDomain.KNOWN_VEGETATION, "found something") { !it.isNullOrEmpty() },
      ),
    )
    val discoverAction = Action(
      name = "discover",
      effects = listOf(Effects.set(BestiaDomain.KNOWN_VEGETATION, listOf(spot))),
    )
    val scout = Agent(
      name = "scout",
      goals = listOf(discoverGoal),
      memory = Blackboard(),
      actionResolver = ActionResolver { listOf(discoverAction) },
      teamMemory = team,
    )

    val plan = planner.makePlanForAgent(scout, world)
    executor.execute(plan!!, scout, world)

    assertEquals(listOf(spot), team.get(BestiaDomain.KNOWN_VEGETATION))

    val packmate = Agent(
      name = "packmate",
      goals = emptyList(),
      memory = Blackboard(),
      actionResolver = ActionResolver { emptyList() },
      teamMemory = team,
    )
    assertEquals(listOf(spot), packmate.snapshotState(world).get(BestiaDomain.KNOWN_VEGETATION))
  }

  @Test
  fun `attack effectiveness learned by one pack is visible world-wide to an unrelated pack`() {
    val world = Blackboard()
    AttackEffectiveness.record(world, EffectivenessKey("golem", "fireBolt"), observed = 0.9)

    val otherPackAgent = Agent(
      name = "other-pack-golem-hunter",
      goals = emptyList(),
      memory = Blackboard(),
      actionResolver = ActionResolver { emptyList() },
      teamMemory = Blackboard(),
    )

    val estimate = otherPackAgent.snapshotState(world)
      .get(BestiaDomain.ATTACK_EFFECTIVENESS)
      ?.get(EffectivenessKey("golem", "fireBolt"))

    assertEquals(0.5 + 0.3 * (0.9 - 0.5), estimate)
  }
}
