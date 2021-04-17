package net.bestia.ai.planner.goap.condition

import net.bestia.ai.firstIsInstanceOrNull

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
      else -> Integer.max(0, amount - rh.amount)
    }
  }
}