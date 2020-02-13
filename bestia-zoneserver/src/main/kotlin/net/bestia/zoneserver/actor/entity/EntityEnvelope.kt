package net.bestia.zoneserver.actor.entity

import net.bestia.messages.entity.EntityMessage
import net.bestia.messages.Envelope

data class EntityEnvelope(
    override val entityId: Long,
    override val content: Any
) : Envelope, EntityMessage