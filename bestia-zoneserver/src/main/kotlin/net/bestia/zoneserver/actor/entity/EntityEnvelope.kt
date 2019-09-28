package net.bestia.zoneserver.actor.entity

import net.bestia.messages.Envelope
import net.bestia.zoneserver.actor.entity.component.ComponentEnvelope
import net.bestia.zoneserver.entity.component.Component

internal data class EntityEnvelope(
    val entityId: Long,
    override val content: Any
) : Envelope

internal inline fun <reified T : Component> makeEntityComponentEnvelope(
    entityId: Long,
    componentClass: Class<T>,
    payload: Any
): EntityEnvelope {
  return EntityEnvelope(entityId, ComponentEnvelope(componentClass, payload))
}