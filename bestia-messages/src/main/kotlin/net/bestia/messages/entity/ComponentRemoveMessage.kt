package net.bestia.messages.entity

import java.io.Serializable

data class ComponentRemoveMessage(
        val componentId: Long
) : Serializable