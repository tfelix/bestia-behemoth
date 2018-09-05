package net.bestia.messages.entity

import net.bestia.messages.Envelope

data class EntityEnvelope(
        val entityId: Long,
        override val content: Any
) : Envelope