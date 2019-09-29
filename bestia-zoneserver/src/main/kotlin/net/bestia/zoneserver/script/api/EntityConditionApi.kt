package net.bestia.zoneserver.script.api

class EntityConditionApi(
    private val conditionContext: EntityConditionContext
) {
  fun addHp(hp: Long): EntityConditionApi {
    conditionContext.commands.add(AddHp(hp))

    return this
  }

  fun setHp(hp: Long): EntityConditionApi {
    conditionContext.commands.add(SetHp(hp))

    return this
  }

  fun addMana(mana: Long): EntityConditionApi {
    conditionContext.commands.add(AddMana(mana))

    return this
  }

  fun setMana(mana: Long): EntityConditionApi {
    conditionContext.commands.add(SetMana(mana))

    return this
  }
}