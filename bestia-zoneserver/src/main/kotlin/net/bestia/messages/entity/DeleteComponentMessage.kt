package net.bestia.messages.entity

import net.bestia.zoneserver.entity.component.Component

data class DeleteComponentMessage<T : Component>(
    val componentClass: Class<T>
)