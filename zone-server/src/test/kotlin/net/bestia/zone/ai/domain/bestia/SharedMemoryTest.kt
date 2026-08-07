package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.core.agent.Agent
import net.bestia.zone.ai.core.agent.SimpleAgent
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.goal.Goal
import net.bestia.zone.ai.core.goal.priority
import net.bestia.zone.ai.core.planner.PlanExecutor
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.precondition.Preconditions
import net.bestia.zone.ai.core.state.Blackboard
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
    val scout = SimpleAgent(
      name = "scout",
      goals = listOf(discoverGoal),
      memory = Blackboard(),
      actionResolver = ActionResolver { listOf(discoverAction) },
      teamMemory = team,
    )

    val plan = planner.makePlanForAgent(scout, world)
    executor.execute(plan!!, scout, world)

    assertEquals(listOf(spot), team.get(BestiaDomain.KNOWN_VEGETATION))

    val packmate = SimpleAgent(
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

    val otherPackAgent = SimpleAgent(
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
