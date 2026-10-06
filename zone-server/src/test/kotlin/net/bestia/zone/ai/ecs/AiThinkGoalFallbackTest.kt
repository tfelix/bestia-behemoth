package net.bestia.zone.ai.ecs

import io.mockk.mockk
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.goal.Goal
import net.bestia.zone.ai.core.goal.priority
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.precondition.Preconditions
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.aoi.ActivePlayerAOIService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A goal no plan exists for is set aside and the next one is pursued; the search a tick may run is capped. */
class AiThinkGoalFallbackTest {

  private val fed = StateKey<Boolean>("fallbackTest.fed")
  private val rested = StateKey<Boolean>("fallbackTest.rested")
  private val steps = StateKey<Int>("fallbackTest.steps")

  /** What the resolver offers; a test adds to it to make a goal plannable later. */
  private val actions = mutableListOf(Action(name = "rest", effects = listOf(Effects.set(rested, true))))

  private val throttle = AiThrottle(AiThrottleConfig(), ActivePlayerAOIService(), mockk(relaxed = true))
  private val world = testWorld(systems = listOf(AiThinkSystem(Planner(), SharedMemoryService(), throttle)))

  @Test
  fun `an unplannable goal gives way to the next one`() {
    val id = agent(listOf(goal("Eat", 90f, fed), goal("Rest", 10f, rested)))

    world.tick(0.05f)

    assertEquals("Rest", agentOf(id).currentGoal?.name, "it does something rather than nothing")
  }

  @Test
  fun `the set-aside goal is tried again later`() {
    val id = agent(listOf(goal("Eat", 90f, fed), goal("Rest", 10f, rested)))
    world.tick(0.05f)

    actions += Action(name = "eat", effects = listOf(Effects.set(fed, true)))
    repeat(AiThinkSystem.GOAL_RETRY_TICKS.toInt() + 20) { world.tick(0.05f) }

    assertEquals("Eat", agentOf(id).currentGoal?.name)
  }

  @Test
  fun `a tick's planning budget leaves the rest to the next tick`() {
    // Every step reaches a new state and none reaches the goal, so one search uses all its iterations.
    actions.clear()
    actions += Action(name = "step", effects = listOf(Effects.modify(steps) { (it ?: 0) + 1 }))
    val endless = Goal(
      name = "Endless",
      priority = priority(base = 50f),
      availability = Precondition { true },
      desiredState = listOf(Preconditions.equalTo(steps, -1)),
    )
    val first = agent(listOf(endless))
    val second = agent(listOf(endless))

    world.tick(0.05f)
    val tick = world.tickCount

    val (searched, deferred) = listOf(first, second).map { agentOf(it) }.partition { it.isGoalBlocked("Endless", tick) }
    assertEquals(1, searched.size, "one agent searched and gave up on its goal")
    assertEquals(tick + 1, deferred.single().nextThinkTick, "the other was not tried and thinks next tick")
  }

  private fun goal(name: String, base: Float, desired: StateKey<Boolean>): Goal {
    return Goal(
      name = name,
      priority = priority(base = base),
      availability = Precondition { true },
      desiredState = listOf(Preconditions.equalTo(desired, true)),
    )
  }

  private fun agent(goals: List<Goal>): EntityId {
    val agent = AiAgent(
      profileId = "test",
      name = "fallback",
      goals = goals,
      actionResolver = ActionResolver { actions },
      memory = Blackboard(),
    )
    agent.hasPerceived = true

    return world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
      add(id, agent)
    }
  }

  private fun agentOf(id: EntityId): AiAgent {
    return world.getOrThrow(id, AiAgent::class)
  }
}
