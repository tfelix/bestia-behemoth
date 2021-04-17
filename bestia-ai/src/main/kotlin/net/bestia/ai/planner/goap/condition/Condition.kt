package net.bestia.ai.planner.goap.condition

/**
 * Describes a condition of an agent. This can be a state, a possession of a certain item,
 * a position at a certain place and so on.
 */
interface Condition {
  /**
   * Returns true only if the condition is completly fullfilled by the given set of conditions.
   * E.g. Health(50) fullfills Health(40).
   */
  fun isFulfilledBy(rhs: Set<Condition>): Boolean

  /**
   * Returns the "distance" towards a goal. Should quantify how "near" of "far" a set of conditions
   * is away from fulfilling this condition.
   * Mininum return is 0 because a target can only be fulfilled completely.
   *
   * Examples:
   * Health(50).fulfillDistance(setOf(Health(55))) = 5
   * Health(50).fulfillDistance(setOf(Health(50))) = 0
   * Health(50).fulfillDistance(setOf(Health(40))) = 0
   */
  fun fulfillDistance(rhs: Set<Condition>): Int
}

data class CanMove(
    val state: Boolean
) : BooleanCondition("CanMove", state)