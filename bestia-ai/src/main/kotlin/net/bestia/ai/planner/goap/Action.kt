package net.bestia.ai.planner.goap

data class Effect(
    val meta: Map<String, Any>
) {

  fun apply(state: MutableSet<Precondition>) {
    val itemState = state.firstOrNull { it.name == "HasItem" && it.meta["itemId"] == meta["itemId"] }
        ?: Precondition.hasItem(meta["itemId"] as Long, 0)

    itemState.meta["amount"] = itemState.meta["amount"] as Int + meta["amount"] as Int
  }

  fun remove(state: MutableSet<Precondition>) {

  }

  companion object {
    fun hasItem(itemId: Long, amount: Int): Effect {
      return Effect(mapOf(
          "HasItem" to itemId,
          "Amount" to amount
      ))
    }
  }
}

data class Action(
    val name: String,
    val cost: Int,
    val preconditions: Set<Precondition>,
    val effects: Map<String, Any>,
    val meta: Map<String, Any>
) {

  fun isPossible(conditions: Set<Precondition>): Boolean {
    return preconditions.all { it.isFulfilledBy(conditions) }
  }

  fun applyEffects(worldState: MutableMap<String, Any>) {
    TODO("Not yet implemented")
  }

  companion object {
    fun useItem(itemId: Long, amount: Int, effects: Map<String, Any>): Action {
      return Action(
          name = "HasItem",
          cost = 1,
          preconditions = setOf(Precondition.hasItem(itemId, amount)),
          meta = mapOf(
              "use" to "item",
              "itemId" to itemId
          ),
          effects = effects
      )
    }

    fun useSpell(spellId: Long, manaCost: Int, effects: Map<String, Any>): Action {
      return Action(
          name = "UseSpell",
          cost = 1,
          preconditions = setOf(Precondition.hasMana(10)),
          meta = mapOf(
              "healing" to 100
          ),
          effects = effects
      )
    }

    fun getItem(itemId: Long): Action {
      return Action(
          name = "GetItem",
          cost = 2,
          preconditions = setOf(
              Precondition(
                  name = "HasItem",
                  meta = mapOf(
                      "itemId" to itemId
                  )
              )
          ),
          meta = mapOf(
              "use" to "item",
              "itemId" to itemId
          ),
          effects = mapOf(
              "hasItem" to itemId
          )
      )
    }
  }
}