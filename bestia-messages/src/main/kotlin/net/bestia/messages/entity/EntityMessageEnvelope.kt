package net.bestia.messages.entity

import net.bestia.messages.Envelope

class EntityMessageEnvelope(
        val entityId: Long,
        content: Any
) : Envelope(content)