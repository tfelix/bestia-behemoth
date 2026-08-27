package net.bestia.zone.ai.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Second stage of the AI pipeline: pick a goal and, when necessary, plan for it. It only produces plans;
 * [AiActSystem] carries them out.
 *
 * ### Two guards, because A* is the expensive half
 *
 * *Replan only when it matters.* Selecting a goal is a handful of predicate evaluations; searching for a
 * plan is A* over a grounded action space. So the goal is selected every time this runs, but the search
 * happens only if the winning goal has changed or the current plan has run out. Without that guard a mob
 * re-plans an identical plan several times a second.
 *
 * *Spread the agents out.* This runs [Schedule.EveryTick] but each agent only thinks every
 * [THINK_PERIOD_TICKS], offset by its own entity id. That matters because the alternative — scheduling
 * the whole system every half second — makes every mob in the zone think on the *same* tick, so a hundred
 * mobs seeing a player all run A* inside one tick and the frame stalls. Staggering turns that spike into a
 * flat cost. `World.tickCount` exists for exactly this and previously had no user.
 */
@SpringComponent
@Order(20)
class AiThinkSystem(
  private val planner: Planner,
  private val sharedMemory: SharedMemoryService,
) : System {

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = setOf(Position::class, PlayerControlled::class, Dead::class)

  /** Written: the goal/plan/behaviour-tree fields and the agent's memory snapshot. */
  override val writes: ComponentClassSet = setOf(AiAgent::class)

  override fun update(world: World, deltaTime: Float) {
    val worldBoard = sharedMemory.worldBoard()

    world.query(AiAgent::class, Position::class).each { id ->
      val agent = get<AiAgent>()

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

      // Never reason from a memory nothing has been observed into — see AiAgent.hasPerceived. Deliberately
      // before the stagger gate, so the first real think happens as soon as perception lands rather than a
      // period later.
      if (!agent.hasPerceived) return@each

      if (world.tickCount < agent.nextThinkTick) return@each
      // Offset by id so agents created on the same tick still land on different ticks from here on.
      agent.nextThinkTick = world.tickCount + THINK_PERIOD_TICKS + (id % THINK_PERIOD_TICKS)

      val state = agent.snapshotState(worldBoard)
      val goal = planner.selectCurrentGoal(agent, state)

      if (goal == null) {
        // Nothing worth doing. Dropping the plan is right: holding a stale one would have the act stage
        // keep executing a goal the agent no longer has any reason to pursue.
        if (agent.hasActivePlan()) agent.clearPlan()
        return@each
      }

      val goalUnchanged = agent.currentGoal?.name == goal.name
      if (goalUnchanged && agent.hasActivePlan()) return@each

      val plan = planner.planFor(agent, goal, state)
      if (plan == null || plan.isEmpty) {
        // Goal unreachable, or already satisfied so there is nothing to do for it. Either way there is no
        // plan to hold; the next think will reconsider.
        agent.clearPlan()
        return@each
      }

      agent.adopt(goal, plan, state)
      LOG.trace { "Entity $id adopts goal '${goal.name}' with plan ${plan.actions.map { it.name }}" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** ~0.5s at the default 20 tps, and the width of the window agents are spread across. */
    private const val THINK_PERIOD_TICKS = 10L
  }
}
