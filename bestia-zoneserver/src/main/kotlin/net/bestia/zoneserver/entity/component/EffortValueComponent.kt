package net.bestia.zoneserver.entity.component

import net.bestia.model.bestia.BaseValues

data class EffortValueComponent(
    override val entityId: Long,
    val effortValues: BaseValues = BaseValues.NULL_VALUES
): Component