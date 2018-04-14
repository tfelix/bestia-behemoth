package net.bestia.messages.entity

import net.bestia.messages.Envelope

data class ToEntityEnvelope(
        val entityId: Long,
        override val content: Any
) : Envelope