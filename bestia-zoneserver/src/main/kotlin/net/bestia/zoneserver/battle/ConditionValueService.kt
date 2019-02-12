package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.bestia.BaseValues
import net.bestia.model.bestia.ConditionValues
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.StatusComponent

private val LOG = KotlinLogging.logger { }

class ConditionValueService {

  fun calculateConditionValues(entity: Entity): ConditionValues {
    val lv = entity.tryGetComponent(LevelComponent::class.java)?.level ?: 1
    val status = entity.getComponent(StatusComponent::class.java)

    status.conditionValues
  }


}