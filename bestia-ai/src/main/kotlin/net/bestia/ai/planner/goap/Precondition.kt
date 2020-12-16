package net.bestia.ai.planner.goap

data class Precondition(
    val name: String,
    val meta: MutableMap<String, Any>
) {
  fun isFulfilledBy(rhs: Set<Precondition>): Boolean {
    return rhs.any { it.meta == meta }
  }

  companion object {
    fun atPosition(x: Long, y: Long, z: Long): Precondition {
      return Precondition(
          name = "AtPos",
          meta = mutableMapOf(
              "x" to x,
              "y" to y,
              "z" to z
          )
      )
    }

    fun hasMana(amount: Int): Precondition {
      return Precondition(
          name = "HasMana",
          meta = mutableMapOf(
              "amount" to amount
          )
      )
    }

    fun hasItem(itemId: Long, amount: Int): Precondition {
      return Precondition(
          name = "HasItem",
          meta = mutableMapOf(
              "item_id" to itemId,
              "amount" to amount
          )
      )
    }
  }
}