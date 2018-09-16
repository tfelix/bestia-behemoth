package net.bestia.messages.entity

import net.bestia.zoneserver.entity.component.Component

data class ResponseComponentMessage<out T : Component>(
        val component: T
)