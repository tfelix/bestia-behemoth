package net.bestia.zoneserver.entity.component

import net.bestia.model.battle.Element
import net.bestia.model.bestia.StatusValues

data class OriginalStatusComponent(
    override val entityId: Long,
    val statusValues: StatusValues,
    val element: Element = Element.NORMAL
): Component