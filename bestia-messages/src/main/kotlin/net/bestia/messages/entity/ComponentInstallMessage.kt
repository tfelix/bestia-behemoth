package net.bestia.messages.entity

import java.io.Serializable

data class ComponentInstallMessage(
        val entityId: Long,
        val componentId: Long
) : Serializable