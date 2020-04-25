package net.bestia.zoneserver.actor.entity.commands

import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.entity.component.ComponentMessage
import net.bestia.zoneserver.entity.component.Component

data class UpdateComponentCommand<T : Component>(
    val component: T
) : EntityMessage, ComponentMessage<T> {
  override val entityId: Long
    get() = component.entityId

  override val componentType: Class<out T>
    get() = component.javaClass
}