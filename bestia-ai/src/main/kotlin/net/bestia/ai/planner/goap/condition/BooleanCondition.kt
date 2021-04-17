package net.bestia.ai.planner.goap.condition

abstract class BooleanCondition(
    val name: String,
    private val state: Boolean
) : Condition {
  override fun isFulfilledBy(rhs: Set<Condition>): Boolean {
    return rhs.filterIsInstance(BooleanCondition::class.java)
        .single { it.name == name }
        .state == state
  }

  override fun fulfillDistance(rhs: Set<Condition>): Int {
    TODO("Not yet implemented")
  }
}