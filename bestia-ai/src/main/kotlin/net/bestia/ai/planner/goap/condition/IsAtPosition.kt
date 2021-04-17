package net.bestia.ai.planner.goap.condition

import net.bestia.ai.firstIsInstanceOrNull

data class IsAtPosition(
    val x: Long, val y: Long, val z: Long
) : Condition {
  override fun isFulfilledBy(rhs: Set<Condition>): Boolean {
    return rhs.filterIsInstance(IsAtPosition::class.java).any { it == this }
  }

  override fun fulfillDistance(rhs: Set<Condition>): Int {
    val rh = rhs.firstIsInstanceOrNull(IsAtPosition::class.java)
        ?: return Int.MAX_VALUE

    val dx = x - rh.x
    val dy = y - rh.y
    val dz = z - rh.z

    // TODO Protect against overflow
    return (dx * dx + dy * dy + dz * dz).toInt()
  }
}