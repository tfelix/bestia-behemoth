package net.bestia.zoneserver.actor.entity

import net.bestia.messages.EntityMessage
import net.bestia.messages.Envelope
import net.bestia.zoneserver.actor.entity.component.ComponentEnvelope
import net.bestia.zoneserver.entity.component.Component

data class EntityEnvelope(
    override val entityId: Long,
    override val content: Any
) : Envelope, EntityMessage

internal inline fun <reified T : Component> makeEntityComponentEnvelope(
    entityId: Long,
    componentClass: Class<T>,
    payload: Any
): EntityEnvelope {
  return EntityEnvelope(entityId, ComponentEnvelope(componentClass, payload))
}