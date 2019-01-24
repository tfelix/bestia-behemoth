package net.bestia.zoneserver.script.api

class EntityConditionApi(
    private val conditionContext: EntityConditionContext
) {
  fun addHp(hp: Long): EntityConditionApi {
    conditionContext.deltaHP += hp
    conditionContext.setHP = null

    return this
  }

  fun setHp(hp: Long): EntityConditionApi {
    conditionContext.deltaHP = 0
    conditionContext.setHP = hp

    return this
  }

  fun addMana(mana: Long): EntityConditionApi {
    conditionContext.deltaMana += mana
    conditionContext.setMana = null

    return this
  }

  fun setMana(mana: Long): EntityConditionApi {
    conditionContext.deltaMana = 0
    conditionContext.setMana = mana

    return this
  }
}