package net.bestia.zoneserver.actor.entity

import net.bestia.zoneserver.entity.Entity

data class EntityResponse(
    val entity: Entity,
    val context: Any? = null
)