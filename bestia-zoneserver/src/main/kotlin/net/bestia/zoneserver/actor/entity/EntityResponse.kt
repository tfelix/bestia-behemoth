package net.bestia.zoneserver.actor.entity

import net.bestia.zoneserver.entity.Entity

internal data class EntityResponse(
    val entity: Entity,
    val context: Any? = null
)