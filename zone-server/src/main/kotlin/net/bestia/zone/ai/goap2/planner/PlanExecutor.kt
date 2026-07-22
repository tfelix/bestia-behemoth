package net.bestia.zone.ai.goap2.planner

import net.bestia.zone.ai.goap2.agent.Agent
import net.bestia.zone.ai.goap2.state.Blackboard
import net.bestia.zone.ai.goap2.state.MemoryScope
import net.bestia.zone.ai.goap2.state.StateKey
import net.bestia.zone.ai.goap2.state.WorldState
import org.slf4j.LoggerFactory

/**
 * Carries out a [Plan] one action at a time against a live [Blackboard], writing
 * back whatever each action's effects changed. This is the "act" half of the
 * sense -> plan -> act loop: [Planner] only simulates on immutable [WorldState]
 * snapshots, so something has to translate a chosen action's effects back into
 * the agent's persistent memory.
 *
 * Preconditions are re-checked before every step (not just once at plan time)
 * because the world can move on between planning and acting; if a step is no
 * longer applicable, execution stops early rather than applying a nonsensical
 * effect.
 */
class PlanExecutor {

  private val log = LoggerFactory.getLogger(PlanExecutor::class.java)

  /**
   * Executes [plan] against [memory], with [world] layered underneath it the
   * same way [Planner] does. Returns the number of actions actually completed,
   * which is less than `plan.actions.size` if a step's preconditions failed.
   */
  fun execute(plan: Plan, memory: Blackboard, world: Blackboard = Blackboard()): Int {
    log.info("executing {}", plan)
    var state = world.snapshotMergedWith(memory)

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
      applyDiff(state, next, memory)

      log.info("step {}/{} executed '{}' (cost={}) -> {}", index + 1, plan.actions.size, action.name, cost, next)
      state = next
    }

    log.info("finished executing {}", plan)
    return plan.actions.size
  }

  /**
   * Executes [plan] for [agent], the scope-aware sibling of [execute]: instead of writing every
   * touched key to a single [Blackboard], each key's [MemoryScope] decides how far its new value
   * cascades — always to [Agent.memory], additionally to [Agent.teamMemory] for [MemoryScope.TEAM]
   * (and up), and additionally to [world] for [MemoryScope.WORLD]. This is what makes "the pack
   * shares foraging spots" or "the species learns which attacks work" fall out of an ordinary effect
   * instead of needing bespoke broadcast code.
   */
  fun execute(plan: Plan, agent: Agent, world: Blackboard = Blackboard()): Int {
    log.info("executing {}", plan)
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
      applyDiff(state, next, agent, world)

      log.info("step {}/{} executed '{}' (cost={}) -> {}", index + 1, plan.actions.size, action.name, cost, next)
      state = next
    }

    log.info("finished executing {}", plan)
    return plan.actions.size
  }

  /** Writes back only the keys [before]/[after] actually disagree on. */
  private fun applyDiff(before: WorldState, after: WorldState, memory: Blackboard) {
    val touchedKeys = before.keys() + after.keys()
    for (key in touchedKeys) {
      @Suppress("UNCHECKED_CAST")
      applyKeyDiff(key as StateKey<Any?>, before, after, memory)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> applyKeyDiff(key: StateKey<T>, before: WorldState, after: WorldState, memory: Blackboard) {
    val beforeValue = before.get(key)
    val afterValue = after.get(key)
    if (beforeValue == afterValue) return

    if (after.contains(key)) {
      memory.set(key, afterValue as T)
    } else {
      memory.remove(key)
    }
  }

  /** Scope-aware sibling of [applyDiff] used by the [Agent]-based [execute] overload. */
  private fun applyDiff(before: WorldState, after: WorldState, agent: Agent, world: Blackboard) {
    val touchedKeys = before.keys() + after.keys()
    for (key in touchedKeys) {
      @Suppress("UNCHECKED_CAST")
      applyKeyDiff(key as StateKey<Any?>, before, after, agent, world)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> applyKeyDiff(key: StateKey<T>, before: WorldState, after: WorldState, agent: Agent, world: Blackboard) {
    val beforeValue = before.get(key)
    val afterValue = after.get(key)
    if (beforeValue == afterValue) return

    val targets = buildList {
      add(agent.memory)
      if (key.scope >= MemoryScope.TEAM) agent.teamMemory?.let(::add)
      if (key.scope == MemoryScope.WORLD) add(world)
    }
    for (board in targets) {
      if (after.contains(key)) board.set(key, afterValue as T) else board.remove(key)
    }
  }
}
