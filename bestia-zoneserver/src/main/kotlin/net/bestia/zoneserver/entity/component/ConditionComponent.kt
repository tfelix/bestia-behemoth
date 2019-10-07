package net.bestia.zoneserver.entity.component

import net.bestia.model.bestia.ConditionValues

data class ConditionComponent(
    override val entityId: Long,
    val conditionValues: ConditionValues
): Component