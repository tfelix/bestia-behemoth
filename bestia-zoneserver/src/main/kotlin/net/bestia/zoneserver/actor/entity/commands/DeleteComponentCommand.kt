package net.bestia.zoneserver.actor.entity.commands

import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.entity.component.Component

data class DeleteComponentCommand<T : Component>(
    override val entityId: Long,
    val componentClass: Class<T>
) : EntityMessage