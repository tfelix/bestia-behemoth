package net.bestia.zoneserver.actor.entity.transmit

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.Component

data class TransmitCommand(
    val changedComponent: Component,
    val entity: Entity,
    val receivingClientIds: Set<Long>
)