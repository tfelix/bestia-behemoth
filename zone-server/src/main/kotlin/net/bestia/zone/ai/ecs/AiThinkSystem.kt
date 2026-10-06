package net.bestia.zone.ai.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.core.planner.PlanningBudget
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.TickBuckets
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import org.springframework.stereotype.Component as SpringComponent

/**
 * Second stage of the AI pipeline: pick a goal and, when necessary, plan for it. It only produces plans;
 * [AiActSystem] carries them out.
 *
 * ### Three guards, because A* is the expensive half
 *
 * *Replan only when it matters.* Selecting a goal is a handful of predicate evaluations; searching for a
 * plan is A* over a grounded action space. So the goal is selected every time this runs, but the search
 * happens only if the winning goal has changed or the current plan has run out. Without that guard a mob
 * re-plans an identical plan several times a second.
 *
 * *Spread the agents out.* This runs [Schedule.EveryTick] but each agent only thinks every
 * [THINK_PERIOD_TICKS] (times its tier's factor, see [AiThrottle]), on a tick bucket of its own. That matters
 * because the alternative — scheduling the whole system every half second — makes every mob in the zone
 * think on the *same* tick, so a hundred mobs seeing a player all run A* inside one tick and the frame
 * stalls. Staggering turns that spike into a flat cost.
 *
 * *Cap a tick's searches.* [PLANNING_BUDGET_PER_TICK] iterations are shared by everyone thinking on a tick.
 */
@SpringComponent
class AiThinkSystem(
  private val planner: Planner,
  private val sharedMemory: SharedMemoryService,
  private val throttle: AiThrottle,
) : System {
  override val phase = Phase.AI
  override val after = setOf(AiDriveSystem::class)

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = setOf(
    Position::class,
    PlayerControlled::class,
    Dead::class,
  )

  /** Written: the goal/plan/behaviour-tree fields and the agent's memory snapshot. */
  override val writes: ComponentClassSet = setOf(AiAgent::class)

  override fun update(world: World, deltaTime: Float) {
    val worldBoard = sharedMemory.worldBoard()
    val tick = world.tickCount
    val budget = PlanningBudget(PLANNING_BUDGET_PER_TICK)

    world.query(AiAgent::class, Position::class).each { id ->
      val agent = get<AiAgent>()

      // First, because nine visits in ten end here and the checks below each cost a lookup.
      if (tick < agent.nextThinkTick) return@each

      // An owned bestia keeps its body - and its agent - after it dies, so without this it would go
      // on planning and walk its own corpse away. The plan is dropped rather than frozen, for the
      // same reason as below: it should decide afresh once it is back on its feet.
      if (world.has(id, Dead::class)) {
        if (agent.hasActivePlan()) agent.clearPlan()
        return@each
      }

      // The player is driving this one; it has no business making up its own mind. Its plan is dropped rather
      // than frozen, so when control is handed back it decides afresh from the world as it is then, instead of
      // resuming an errand chosen before the player took over.
      if (world.has(id, PlayerControlled::class)) {
        if (agent.hasActivePlan()) agent.clearPlan()
        return@each
      }

      // Never reason from a memory nothing has been observed into — see AiAgent.hasPerceived. The think tick
      // has not moved yet, so the first real think happens as soon as perception lands.
      if (!agent.hasPerceived) return@each

      agent.nextThinkTick = TickBuckets.nextDue(tick, id, THINK_PERIOD_TICKS * throttle.factorOf(agent))

      val state = agent.snapshotState(worldBoard)

      // A goal no plan was found for is set aside for a while and the next one is tried, rather than the agent
      // standing still and failing the same search on every think.
      repeat(MAX_GOALS_PER_THINK) {
        val goal = planner.selectCurrentGoal(agent, state) { agent.isGoalBlocked(it.name, tick) }

        if (goal == null) {
          // Nothing worth doing. Dropping the plan is right: holding a stale one would have the act stage
          // keep executing a goal the agent no longer has any reason to pursue.
          if (agent.hasActivePlan()) agent.clearPlan()
          return@each
        }

        val goalUnchanged = agent.currentGoal?.name == goal.name
        if (goalUnchanged && agent.hasActivePlan()) return@each

        // The search is the expensive part, so one tick only gets so much of it; whoever is left thinks next tick.
        if (budget.isSpent) {
          agent.nextThinkTick = tick + 1
          return@each
        }

        val plan = planner.planFor(agent, goal, state, budget)
        if (plan != null && !plan.isEmpty) {
          agent.adopt(goal, plan, state)
          LOG.trace { "Entity $id adopts goal '${goal.name}' with plan ${plan.actions.map { it.name }}" }
          return@each
        }

        agent.blockGoal(goal.name, untilTick = tick + GOAL_RETRY_TICKS)
      }

      // Out of attempts. The next think goes on down the list, with these goals set aside.
      agent.clearPlan()
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** ~0.5s at the default 20 tps, and the width of the window agents are spread across. */
    private const val THINK_PERIOD_TICKS = 10L

    /** Goals tried in one think before the agent gives up until its next one. */
    private const val MAX_GOALS_PER_THINK = 3

    /** How long a goal no plan was found for is set aside: 5 s at 20 tps, long enough for the world to change. */
    const val GOAL_RETRY_TICKS = 100L

    /** Search iterations for all agents in one tick. A search already started still runs to its end. */
    const val PLANNING_BUDGET_PER_TICK = 5_000
  }
}
