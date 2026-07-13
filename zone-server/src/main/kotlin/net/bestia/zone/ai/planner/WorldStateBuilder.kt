package net.bestia.zone.ai.planner

import net.bestia.zone.ai.Brain
import net.bestia.zone.ai.goal.consideration.DecisionContext
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Service

/**
 * Derives the symbolic [WorldState] the planner reasons over from the NPC's brain snapshot, its
 * distilled [DecisionContext] and its own position. All inputs are already lock-free snapshots, so
 * no foreign entity is touched here.
 *
 * [StateKey.TARGET_DEAD] and [StateKey.AT_WANDER_POINT] are always left false: they are goal-only
 * facts that the corresponding actions produce, which is what makes those goals require a plan.
 */
@Service
class WorldStateBuilder {

  fun build(brain: Brain, context: DecisionContext, selfPosition: Vec3L): WorldState {
    val hasTarget = brain.targetId != null
    val targetInMelee = hasTarget && brain.targetPosition
      ?.let { selfPosition.distance(it) <= brain.meleeRange } == true
    val selfSafe = !context.enemyInSight && context.ownHealthPct > brain.lowHealthThreshold

    return WorldState.of(
      StateKey.HAS_TARGET to hasTarget,
      StateKey.TARGET_IN_MELEE_RANGE to targetInMelee,
      StateKey.TARGET_DEAD to false,
      StateKey.SELF_SAFE to selfSafe,
      StateKey.AT_WANDER_POINT to false
    )
  }
}
