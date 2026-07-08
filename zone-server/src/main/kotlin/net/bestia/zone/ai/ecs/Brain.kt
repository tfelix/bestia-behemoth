package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.goal.Goal
import net.bestia.zone.ai.memory.IndividualMemory
import net.bestia.zone.ai.perception.AiEvent
import net.bestia.zone.ai.planner.Plan
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * All per-NPC AI runtime state, produced by perception/think and consumed by act on the next tick.
 *
 * Deliberately lives under `net.bestia.zone.ai.*` and is **not** [net.bestia.zone.ecs.Dirtyable]:
 * the network layer's dirty-component scan only inspects `net.bestia.zone.ecs`, so this internal
 * brain state never leaks to clients. Client-visible effects reach players "for free" through the
 * existing dirtyable `Path`/`Position`/`Health` components that the leaves mutate.
 *
 * The target is held as a plain [EntityId] (plus a [targetPosition] snapshot) rather than a live
 * entity reference, so the think/act hot path never has to take a foreign read lock to reason about
 * it.
 */
class Brain(
  val profileId: String,
  val memory: IndividualMemory = IndividualMemory(),
  val meleeRange: Long = 1,
  val lowHealthThreshold: Double = 0.35,
  val attackCooldownSeconds: Float = 1.0f
) : Component {

  /** Latest perception sweep result, refreshed by the perception system. */
  var latestPercept: net.bestia.zone.ai.perception.PerceptionSnapshot? = null

  /** Current target, if any. */
  var targetId: EntityId? = null

  /** Last known position of the target, snapshotted so act needs no foreign read lock. */
  var targetPosition: Vec3L? = null

  /** Position of the nearest perceived threat, used when fleeing. */
  var threatPosition: Vec3L? = null

  var currentGoal: Goal? = null
  var currentPlan: Plan? = null
  var planCursor: Int = 0

  /** Behaviour tree of the currently executing plan action. */
  var currentActionNode: BtNode? = null

  /** Remaining attack cooldown in seconds; counts down every act tick. */
  var attackCooldownRemaining: Float = 0f

  private val events = ArrayDeque<AiEvent>()

  fun pushEvent(event: AiEvent) {
    events.addLast(event)
  }

  fun drainEvents(): List<AiEvent> {
    val drained = events.toList()
    events.clear()
    return drained
  }

  fun currentAction() = currentPlan?.actions?.getOrNull(planCursor)

  /** Advance to the next action; returns its behaviour tree, or null when the plan is finished. */
  fun advancePlan(): BtNode? {
    planCursor++
    val next = currentAction() ?: return null
    currentActionNode = next.behaviorTree()
    return currentActionNode
  }

  fun clearPlan() {
    currentPlan = null
    currentGoal = null
    planCursor = 0
    currentActionNode = null
  }

  fun hasActivePlan(): Boolean = currentActionNode != null
}
