package net.bestia.zoneserver.actor.entity

import net.bestia.messages.Envelope

internal data class EntityEnvelope(
    val entityId: Long,
    override val content: Any
) : Envelope