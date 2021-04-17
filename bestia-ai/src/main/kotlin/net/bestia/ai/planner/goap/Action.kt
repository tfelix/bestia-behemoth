package net.bestia.ai.planner.goap

import net.bestia.ai.planner.goap.condition.Condition
import net.bestia.ai.planner.goap.condition.HasItem
import net.bestia.ai.planner.goap.condition.HasMana
import net.bestia.ai.planner.goap.condition.IsAtPosition
import net.bestia.ai.planner.goap.effect.ChangeItemAmount
import net.bestia.ai.planner.goap.effect.Effect
import net.bestia.ai.planner.goap.effect.SetPosition

data class Action(
    val name: String,
    val cost: Int,
    val conditions: List<Condition>,
    val effects: List<Effect>
) {

  fun isPossible(conditions: Set<Condition>): Boolean {
    return this.conditions.all { it.isFulfilledBy(conditions) }
  }

  fun applyEffects(conditions: Set<Condition>): Set<Condition> {
    return effects.foldRight(conditions) { effect, modifiedConditions -> effect.apply(modifiedConditions) }
  }

  companion object {
    fun useItem(itemId: Long, amount: Int, effects: List<Effect>): Action {
      return Action(
          name = "UseItem",
          cost = 1,
          conditions = listOf(HasItem(itemId, amount)),
          effects = effects + listOf(ChangeItemAmount(itemId, -amount))
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

    fun moveTo(x: Long, y: Long, z: Long, effects: List<Effect>): Action {
      return Action(
          name = "MoveTo",
          cost = 2, // cost should be dynamic depending on distance moved.
          conditions = listOf(),
          effects = effects + SetPosition(x, y, z)
      )
    }

    fun getItem(itemId: Long, x: Long, y: Long, z: Long): Action {
      return Action(
          name = "GetItem",
          cost = 2,
          conditions = listOf(IsAtPosition(x, y, z)),
          effects = listOf(ChangeItemAmount(itemId, 1))
      )
    }
  }
}