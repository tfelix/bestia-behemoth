package net.bestia.zone.ai.goap2.planner

import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.action.ActionResolver
import net.bestia.zone.ai.goap2.agent.Agent
import net.bestia.zone.ai.goap2.goal.Goal
import net.bestia.zone.ai.goap2.state.Blackboard
import net.bestia.zone.ai.goap2.state.WorldState
import org.slf4j.LoggerFactory
import java.util.PriorityQueue

/**
 * Two-phase GOAP planner:
 *  1. **Goal selection** — enrich the world with the agent's memory, keep the
 *     goals that are currently *available*, and pick the one with the highest
 *     dynamic priority.
 *  2. **Forward A\*** — search concrete world states, applying grounded action
 *     effects, until the selected goal's desired state holds.
 *
 * Forward (rather than classic symbolic backward) search is what lets
 * preconditions reason about arbitrary complex values — positions, inventories,
 * item/location maps — because it always works on fully concrete states.
 */
class Planner(
  /** Safety valve so an unsatisfiable goal can never hang the search. */
  private val maxIterations: Int = 10_000,
) {

  private val log = LoggerFactory.getLogger(Planner::class.java)

  fun makePlanForAgent(agent: Agent, worldState: Blackboard): Plan? {
    val start = worldState.snapshotMergedWith(agent.memory)
    log.info("[{}] planning from state {}", agent.name, start)

    val goal = selectCurrentGoal(agent, start) ?: run {
      log.info("[{}] no available/unsatisfied goal to pursue — nothing to plan", agent.name)
      return null
    }

    val plan = search(start, goal, agent.actionResolver)
    if (plan != null) {
      log.info("[{}] found plan: {}", agent.name, plan)
    } else {
      log.warn("[{}] no plan found to satisfy goal '{}' from state {}", agent.name, goal.name, start)
    }
    return plan
  }

  /** Highest-priority goal that is both available and not already satisfied. */
  fun selectCurrentGoal(agent: Agent, state: WorldState): Goal? {
    if (log.isDebugEnabled) {
      agent.goals.forEach { goal ->
        log.debug(
          "[{}] candidate goal '{}': available={}, satisfied={}, priority={}",
          agent.name, goal.name, goal.isAvailable(state), goal.isSatisfiedBy(state), goal.evaluatePriority(state),
        )
      }
    }

    val selected = agent.goals
      .filter { it.isAvailable(state) && !it.isSatisfiedBy(state) }
      .maxByOrNull { it.evaluatePriority(state) }

    if (selected != null) {
      log.info("[{}] selected goal '{}' (priority={})", agent.name, selected.name, selected.evaluatePriority(state))
    }
    return selected
  }

  private class Node(
    val state: WorldState,
    val gCost: Float,
    val fCost: Float,
    val action: Action?,
    val parent: Node?,
  )

  private fun search(start: WorldState, goal: Goal, resolver: ActionResolver): Plan? {
    if (goal.isSatisfiedBy(start)) return Plan(goal, emptyList(), 0f)

    val open = PriorityQueue<Node>(compareBy { it.fCost })
    val bestG = HashMap<WorldState, Float>()
    val closed = HashSet<WorldState>()

    open += Node(start, 0f, goal.heuristic(start).toFloat(), null, null)
    bestG[start] = 0f

    var iterations = 0
    while (open.isNotEmpty() && iterations++ < maxIterations) {
      val current = open.poll()

      if (goal.isSatisfiedBy(current.state)) {
        log.debug("goal '{}' satisfied after {} iterations", goal.name, iterations)
        return reconstruct(goal, current)
      }
      if (!closed.add(current.state)) continue

      for (action in resolver.getAvailableActions(current.state)) {
        if (!action.isApplicable(current.state)) continue

        val next = action.applyTo(current.state)
        if (next in closed) continue

        val tentativeG = current.gCost + action.cost(current.state)
        val knownG = bestG[next]
        if (knownG != null && tentativeG >= knownG) continue

        bestG[next] = tentativeG
        val f = tentativeG + goal.heuristic(next)
        log.trace("goal '{}': expanding '{}' -> {} (g={}, f={})", goal.name, action.name, next, tentativeG, f)
        open += Node(next, tentativeG, f, action, current)
      }
    }
    log.debug("goal '{}': search exhausted after {} iterations, open={}", goal.name, iterations, open.size)
    return null
  }

  private fun reconstruct(goal: Goal, node: Node): Plan {
    val actions = ArrayDeque<Action>()
    var current: Node? = node
    while (current != null) {
      val action = current.action ?: break
      actions.addFirst(action)
      current = current.parent
    }
    return Plan(goal, actions.toList(), node.gCost)
  }
}
