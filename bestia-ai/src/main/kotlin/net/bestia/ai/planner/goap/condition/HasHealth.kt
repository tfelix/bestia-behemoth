package net.bestia.ai.planner.goap.condition

import net.bestia.ai.firstIsInstanceOrNull

data class HasHealth(
    val amount: Int
) : Condition {
  override fun isFulfilledBy(rhs: Set<Condition>): Boolean {
    return rhs.filterIsInstance(HasHealth::class.java).any { it.amount >= this.amount }
  }

  override fun fulfillDistance(rhs: Set<Condition>): Int {
    val rh = rhs.firstIsInstanceOrNull(HasHealth::class.java)
        ?: return Int.MAX_VALUE

    return Integer.max(0, amount - rh.amount)
  }
}