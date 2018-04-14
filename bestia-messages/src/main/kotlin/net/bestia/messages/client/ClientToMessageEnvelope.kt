package net.bestia.messages.client

import net.bestia.messages.Envelope

class ClientToMessageEnvelope(
        val clientAccountId: Long,
        content: Any
) : Envelope(content)