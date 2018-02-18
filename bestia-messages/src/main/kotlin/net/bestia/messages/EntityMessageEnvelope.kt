package net.bestia.messages

class EntityMessageEnvelope(
        val entityId: Long,
        content: Any
) : Envelope(content)