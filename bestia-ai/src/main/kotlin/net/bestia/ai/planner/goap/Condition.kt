package net.bestia.ai.planner.goap

import java.lang.Integer.max

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

data class AtPosition(
    var x: Long, var y: Long, var z: Long
) : Condition {
  override fun isFulfilledBy(rhs: Set<Condition>): Boolean {
    return rhs.filterIsInstance(AtPosition::class.java).any { it == this }
  }

  override fun fulfillDistance(rhs: Set<Condition>): Int {
    val rh = rhs.firstIsInstanceOrNull(AtPosition::class.java)
        ?: return Int.MAX_VALUE

    val dx = x - rh.x
    val dy = y - rh.y
    val dz = z - rh.z

    // TODO Protect against overflow
    return (dx * dx + dy * dy + dz * dz).toInt()
  }
}

data class HasMana(
    var amount: Int
) : Condition {
  override fun isFulfilledBy(rhs: Set<Condition>): Boolean {
    return rhs.filterIsInstance(HasMana::class.java).any { it.amount >= this.amount }
  }

  override fun fulfillDistance(rhs: Set<Condition>): Int {
    val rh = rhs.firstIsInstanceOrNull(HasMana::class.java)
        ?: return Int.MAX_VALUE

    return max(0, amount - rh.amount)
  }
}

data class HasHealth(
    var amount: Int
) : Condition {
  override fun isFulfilledBy(rhs: Set<Condition>): Boolean {
    return rhs.filterIsInstance(HasHealth::class.java).any { it.amount >= this.amount }
  }

  override fun fulfillDistance(rhs: Set<Condition>): Int {
    val rh = rhs.firstIsInstanceOrNull(HasHealth::class.java)
        ?: return Int.MAX_VALUE

    return max(0, amount - rh.amount)
  }
}

data class HasItem(
    val itemId: Long,
    var amount: Int
) : Condition {
  override fun isFulfilledBy(rhs: Set<Condition>): Boolean {
    return rhs.filterIsInstance(HasItem::class.java).any { it.amount >= this.amount }
  }

  override fun fulfillDistance(rhs: Set<Condition>): Int {
    val rh = rhs.firstIsInstanceOrNull(HasItem::class.java)
        ?: return Int.MAX_VALUE

    return when {
      rh.itemId != itemId -> Int.MAX_VALUE
      else -> max(0, amount - rh.amount)
    }
  }
}