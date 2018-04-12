package net.bestia.messages.client

import net.bestia.messages.Envelope

/**
 * Messages coming from the client are wrapped in this envelope and contain
 * the payload.
 */
class ClientFromMessageEnvelope(
        val accountId: Long,
        content: Any
) : Envelope(content)