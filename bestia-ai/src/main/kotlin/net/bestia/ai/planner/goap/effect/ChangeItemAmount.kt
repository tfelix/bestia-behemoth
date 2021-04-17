package net.bestia.ai.planner.goap.effect

import net.bestia.ai.planner.goap.condition.Condition
import net.bestia.ai.planner.goap.condition.HasItem

data class ChangeItemAmount(
    private val itemId: Long,
    private val amount: Int
) : Effect {
  override fun apply(states: Set<Condition>): Set<Condition> {
    val itemState = states.filterIsInstance(HasItem::class.java)
        .firstOrNull { it.itemId == itemId }

    return when (itemState) {
      null -> states + HasItem(itemId, amount)
      else -> states - itemState + itemState.copy(amount = itemState.amount + amount)
    }
  }
}