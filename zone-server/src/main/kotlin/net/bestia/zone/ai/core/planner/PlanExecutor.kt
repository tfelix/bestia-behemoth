package net.bestia.zone.ai.core.planner

import net.bestia.zone.ai.core.agent.Agent
import net.bestia.zone.ai.core.state.Blackboard
import org.slf4j.LoggerFactory

/**
 * Carries out a whole [Plan] against a live [Blackboard] **as if no time passed**, folding each
 * action's effects back into the agent's memory via [EffectWriteBack].
 *
 * ### This is a simulation harness, not the runtime path
 *
 * In the live game an action takes many ticks: its behaviour tree walks the creature across the
 * world, and only when that tree reports SUCCESS have the action's effects actually happened. That
 * job belongs to the act system, which applies one action's effects at a time — see
 * [EffectWriteBack].
 *
 * What this class is still good for is reasoning about a domain without a world attached: given a
 * start state, does the planner produce a sensible chain, and does that chain leave memory in the
 * expected shape? That is exactly what the domain tests assert, and it stays a genuinely useful way
 * to develop a new domain before wiring any behaviour to it.
 *
 * Preconditions are re-checked before every step rather than trusted from plan time, because a
 * simulated chain can be fed a state the planner never saw.
 */
class PlanExecutor {

  private val log = LoggerFactory.getLogger(PlanExecutor::class.java)

  /**
   * Simulates [plan] for [agent], with [world] layered underneath its memory the way [Planner] does.
   * Returns the number of actions actually completed, which is fewer than `plan.actions.size` if a
   * step's preconditions no longer held.
   */
  fun execute(plan: Plan, agent: Agent, world: Blackboard = Blackboard()): Int {
    log.info("simulating {}", plan)
    var state = agent.snapshotState(world)

    plan.actions.forEachIndexed { index, action ->
      if (!action.isApplicable(state)) {
        log.warn(
          "step {}/{} '{}' is no longer applicable in state {} — aborting the rest of the plan",
          index + 1, plan.actions.size, action.name, state,
        )
        return index
      }

      val cost = action.cost(state)
      val next = action.applyTo(state)
      EffectWriteBack.apply(state, next, agent.memory, agent.teamMemory, world)

      log.info("step {}/{} executed '{}' (cost={}) -> {}", index + 1, plan.actions.size, action.name, cost, next)
      state = next
    }

    log.info("finished simulating {}", plan)
    return plan.actions.size
  }
}
