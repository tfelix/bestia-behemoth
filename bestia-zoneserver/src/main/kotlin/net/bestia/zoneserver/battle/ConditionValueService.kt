package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.bestia.BaseValues
import net.bestia.model.bestia.ConditionValues
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.LevelComponent

private val LOG = KotlinLogging.logger { }

class ConditionValueService {

  fun calculateConditionValues(entity: Entity): ConditionValues {
    val lv = entity.tryGetComponent(LevelComponent::class.java)?.level ?: 1
  }

  fun updateConditionValues(
      conditionValues: ConditionValues = ConditionValues(),
      level: Int = 1,
      bVals: BaseValues,
      iVals: BaseValues = BaseValues.nullValues,
      eVals: BaseValues = BaseValues.nullValues
  ): ConditionValues {

    val maxHp = bVals.hp * 2 + iVals.hp + eVals.hp / 4 * level / 100 + 10 + level
    val maxMana = bVals.mana * 2 + iVals.mana + eVals.mana / 4 * level / 100 + 10 + level * 2

    return conditionValues.setMaximumValues(maxHp, maxMana)
  }
}