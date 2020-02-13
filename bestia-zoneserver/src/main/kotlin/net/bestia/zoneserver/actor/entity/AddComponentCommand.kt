package net.bestia.zoneserver.actor.entity

import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.entity.component.Component

data class AddComponentCommand<out T : Component>(
    val component: T
) : EntityMessage {
  override val entityId: Long
    get() = component.entityId
}