package net.bestia.ai.planner.goap

data class Action(
    val name: String,
    val cost: Int,
    val conditions: List<Condition>,
    val effects: List<Effect>
) {

  fun isPossible(conditions: Set<Condition>): Boolean {
    return this.conditions.all { it.isFulfilledBy(conditions) }
  }

  fun applyEffects(conditions: MutableSet<Condition>) {
    effects.forEach { it.apply(conditions) }
  }

  companion object {
    fun useItem(itemId: Long, amount: Int, effects: List<Effect>): Action {
      return Action(
          name = "HasItem",
          cost = 1,
          conditions = listOf(HasItem(itemId, amount)),
          effects = effects
      )
    }

    fun useSpell(spellId: Long, manaCost: Int, effects: List<Effect>): Action {
      return Action(
          name = "UseSpell",
          cost = 1,
          conditions = listOf(HasMana(10)),
          effects = effects
      )
    }

    fun getItem(itemId: Long): Action {
      return Action(
          name = "GetItem",
          cost = 2,
          conditions = listOf(HasItem(itemId, 1)),
          effects = listOf(HasItemEffect(itemId, 1))
      )
    }
  }
}