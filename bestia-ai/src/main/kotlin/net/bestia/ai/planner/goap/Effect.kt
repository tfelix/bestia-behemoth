package net.bestia.ai.planner.goap

interface Effect {
  fun apply(states: MutableSet<Condition>)
  fun remove(states: MutableSet<Condition>)
}

/**
 * Returns a list containing all elements that are instances of specified class.
 */
public fun <R> Iterable<*>.firstIsInstanceOrNull(klass: Class<R>): R? {
  return filterIsInstanceTo(ArrayList<R>(), klass).firstOrNull()
}

class HasItemEffect(
    private val itemId: Long,
    private val amount: Int
) : Effect {
  override fun apply(state: MutableSet<Condition>) {
    val itemState = state.filterIsInstance(HasItem::class.java)
        .firstOrNull { it.itemId == itemId }

    when (itemState) {
      null -> state.add(HasItem(itemId, amount))
      else -> itemState.amount += amount
    }
  }

  override fun remove(state: MutableSet<Condition>) {
    TODO("Implement")
  }
}

class AddHealth(
    private val amount: Int
) : Effect {
  override fun apply(states: MutableSet<Condition>) {
    when(val state = states.firstIsInstanceOrNull(HasHealth::class.java)) {
      null -> states.add(HasHealth(amount))
      else -> state.amount += amount
    }
  }

  override fun remove(states: MutableSet<Condition>) {
    TODO("Implement")
  }
}

class AddMana(
    private val amount: Int
) : Effect {
  override fun apply(states: MutableSet<Condition>) {
    when(val state = states.firstIsInstanceOrNull(HasMana::class.java)) {
      null -> states.add(HasMana(amount))
      else -> state.amount += amount
    }
  }

  override fun remove(states: MutableSet<Condition>) {
    TODO("Implement")
  }
}