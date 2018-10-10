package net.bestia.messages.entity

import net.bestia.zoneserver.entity.Entity

internal data class EntityResponse(
    val entity: Entity,
    val content: Any? = null
)