package net.bestia.zoneserver.actor.entity.broadcast

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.Component

data class TransmitCommand(
    val changedComponent: Component,
    val entity: Entity,
    val receivingClientIds: List<Long>
)